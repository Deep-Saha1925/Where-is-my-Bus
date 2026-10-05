/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.controller;

import com.deep.WIMB.TestFields;
import com.deep.WIMB.service.ClientIpResolver;
import com.deep.WIMB.service.DriverAccessService;
import com.deep.WIMB.service.DriverTokenService;
import com.deep.WIMB.service.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The driver login flow end to end through the controller, with the real rate limiter and IP resolver. */
class DriverAuthControllerTest {

    private static final String DEPOT = "ALIPURDUAR";
    private static final String CODE  = "101";

    private DriverAuthController controller;

    @BeforeEach
    void setUp() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        TestFields.set(limiter, "windowSeconds", 600L);
        TestFields.set(limiter, "lockoutSeconds", 900L);
        TestFields.set(limiter, "maxPerIp", 10);
        TestFields.set(limiter, "maxPerDepot", 40);
        TestFields.set(limiter, "maxPerBus", 8);

        ClientIpResolver resolver = new ClientIpResolver();
        TestFields.set(resolver, "trustedHops", 1);

        // Only the depot-code check and the token are faked; everything else is real
        DriverAccessService access = new DriverAccessService(null) {
            @Override
            public boolean isValid(String depotName, String code) {
                return DEPOT.equalsIgnoreCase(depotName.trim()) && CODE.equals(code.trim());
            }
        };
        DriverTokenService tokens = new DriverTokenService() {
            @Override
            public String issueToken(String busNumber) {
                return "token-for-" + busNumber.trim().toUpperCase();
            }
        };

        controller = new DriverAuthController(access, tokens, limiter, resolver);
    }

    private static Map<String, String> body(String depot, String code, String bus) {
        Map<String, String> m = new HashMap<>();
        m.put("depotName", depot);
        m.put("code", code);
        m.put("busNumber", bus);
        return m;
    }

    private static HttpServletRequest from(String ip) {
        return from(ip, null);
    }

    private static HttpServletRequest from(String ip, String forwardedFor) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getRemoteAddr": return ip;
                        case "getHeader":
                            return "X-Forwarded-For".equalsIgnoreCase((String) args[0]) ? forwardedFor : null;
                        case "toString": return "fake request from " + ip;
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    private static int status(ResponseEntity<?> r) {
        return r.getStatusCode().value();
    }

    private static Map<?, ?> json(ResponseEntity<?> r) {
        return (Map<?, ?>) r.getBody();
    }

    @Test
    void correctDepotAndCodeReturnsATokenForThatBus() {
        ResponseEntity<?> r = controller.verify(body(DEPOT, CODE, "wb-1"), from("203.0.113.5"));
        assertEquals(200, status(r));
        assertEquals("token-for-WB-1", json(r).get("token"));
    }

    @Test
    void wrongCodeIsRejectedWith401() {
        ResponseEntity<?> r = controller.verify(body(DEPOT, "999", "WB-1"), from("203.0.113.5"));
        assertEquals(401, status(r));
        assertNull(json(r).get("token"));
    }

    @Test
    void missingFieldsAreRejectedWith400() {
        assertEquals(400, status(controller.verify(body(DEPOT, CODE, ""), from("203.0.113.5"))));
        assertEquals(400, status(controller.verify(body(DEPOT, CODE, null), from("203.0.113.5"))));
        assertEquals(400, status(controller.verify(body("", CODE, "WB-1"), from("203.0.113.5"))));
        assertEquals(400, status(controller.verify(body(DEPOT, "  ", "WB-1"), from("203.0.113.5"))));
    }

    @Test
    void overlongFieldsAreRejectedWith400() {
        String huge = "x".repeat(101);
        assertEquals(400, status(controller.verify(body(huge, CODE, "WB-1"), from("203.0.113.5"))));
        assertEquals(400, status(controller.verify(body(DEPOT, huge, "WB-1"), from("203.0.113.5"))));
        assertEquals(400, status(controller.verify(body(DEPOT, CODE, huge), from("203.0.113.5"))));
    }

    @Test
    void eighthWrongCodeForTheSameBusLocksItWith429() {
        for (int i = 1; i <= 7; i++) {
            assertEquals(401, status(controller.verify(body(DEPOT, "bad" + i, "WB-1"), from("203.0.113.5"))),
                    "attempt " + i + " is still just a wrong code");
        }
        ResponseEntity<?> locked = controller.verify(body(DEPOT, "bad8", "WB-1"), from("203.0.113.5"));
        assertEquals(429, status(locked));
        assertNotNull(locked.getHeaders().getFirst("Retry-After"));
        assertTrue(Long.parseLong(locked.getHeaders().getFirst("Retry-After")) > 0);
        assertTrue(((Number) json(locked).get("retryAfterSeconds")).longValue() > 0);
        assertTrue(String.valueOf(json(locked).get("error")).contains("Too many failed attempts"));
    }

    @Test
    void lockedOutClientIsRefusedEvenWithTheCorrectCode() {
        for (int i = 1; i <= 8; i++) controller.verify(body(DEPOT, "bad" + i, "WB-1"), from("203.0.113.5"));
        assertEquals(429, status(controller.verify(body(DEPOT, CODE, "WB-1"), from("203.0.113.5"))));
        // even from a different IP: this bus is locked, whoever asks
        assertEquals(429, status(controller.verify(body(DEPOT, CODE, "WB-1"), from("198.51.100.9"))));
    }

    @Test
    void otherBusesStillWorkWhileOneBusIsLocked() {
        for (int i = 1; i <= 8; i++) controller.verify(body(DEPOT, "bad" + i, "WB-1"), from("203.0.113.5"));
        assertEquals(200, status(controller.verify(body(DEPOT, CODE, "WB-2"), from("198.51.100.9"))));
    }

    @Test
    void oneIpGuessingAcrossManyBusesIsLockedAfterTenFailures() {
        for (int i = 1; i <= 10; i++) {
            controller.verify(body(DEPOT, "bad" + i, "WB-" + i), from("203.0.113.5"));
        }
        assertEquals(429, status(controller.verify(body(DEPOT, CODE, "WB-99"), from("203.0.113.5"))));
        assertEquals(200, status(controller.verify(body(DEPOT, CODE, "WB-99"), from("198.51.100.9"))),
                "a different IP is not affected");
    }

    @Test
    void changingTheForwardedForHeaderDoesNotResetTheLimit() {
        // a public client can send any X-Forwarded-For it likes; it must not help it dodge the IP limit
        for (int i = 1; i <= 10; i++) {
            controller.verify(body(DEPOT, "bad" + i, "WB-" + i), from("203.0.113.5", "1.2.3." + i));
        }
        assertEquals(429, status(controller.verify(body(DEPOT, CODE, "WB-99"), from("203.0.113.5", "8.8.8.8"))));
    }

    @Test
    void behindAnInternalProxyEachRealClientHasItsOwnLimit() {
        // the peer is the proxy (private address); the real client is in X-Forwarded-For
        for (int i = 1; i <= 10; i++) {
            controller.verify(body(DEPOT, "bad" + i, "WB-" + i), from("10.0.0.5", "203.0.113.5"));
        }
        assertEquals(429, status(controller.verify(body(DEPOT, CODE, "WB-99"), from("10.0.0.5", "203.0.113.5"))));
        assertEquals(200, status(controller.verify(body(DEPOT, CODE, "WB-99"), from("10.0.0.5", "198.51.100.77"))),
                "another client behind the same proxy is not locked out");
    }
}
