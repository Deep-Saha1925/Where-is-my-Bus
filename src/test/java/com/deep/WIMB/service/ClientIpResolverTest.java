/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import com.deep.WIMB.TestFields;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {

    /** A request that only knows its peer address and an X-Forwarded-For header. */
    static HttpServletRequest request(String remoteAddr, String forwardedFor) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getRemoteAddr": return remoteAddr;
                        case "getHeader":
                            return "X-Forwarded-For".equalsIgnoreCase((String) args[0]) ? forwardedFor : null;
                        case "toString": return "fake request from " + remoteAddr;
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    private static ClientIpResolver resolver(int hops) {
        ClientIpResolver r = new ClientIpResolver();
        TestFields.set(r, "trustedHops", hops);
        return r;
    }

    @Test
    void connectionFromTheInternetCannotFakeItsIp() {
        // a public peer is not a trusted proxy, so its X-Forwarded-For is ignored
        assertEquals("203.0.113.5", resolver(1).resolve(request("203.0.113.5", "6.6.6.6")));
    }

    @Test
    void internalProxyOneHopUsesTheForwardedClient() {
        assertEquals("198.51.100.7", resolver(1).resolve(request("10.1.2.3", "198.51.100.7")));
    }

    @Test
    void entriesTheClientAddedItselfAreIgnored() {
        // the client sent "6.6.6.6"; the proxy appended the real address after it
        assertEquals("198.51.100.7", resolver(1).resolve(request("10.1.2.3", "6.6.6.6, 198.51.100.7")));
    }

    @Test
    void twoHopsSkipsTheCdnAddress() {
        assertEquals("198.51.100.7",
                resolver(2).resolve(request("10.1.2.3", "198.51.100.7, 172.70.0.1")));
    }

    @Test
    void fewerEntriesThanTrustedHopsFallsBackToThePeer() {
        assertEquals("10.1.2.3", resolver(2).resolve(request("10.1.2.3", "198.51.100.7")));
    }

    @Test
    void missingOrGarbageHeaderFallsBackToThePeer() {
        assertEquals("10.1.2.3", resolver(1).resolve(request("10.1.2.3", null)));
        assertEquals("127.0.0.1", resolver(1).resolve(request("127.0.0.1", "<script>alert(1)</script>")));
    }

    @Test
    void zeroHopsNeverTrustsTheHeader() {
        assertEquals("10.1.2.3", resolver(0).resolve(request("10.1.2.3", "198.51.100.7")));
    }

    @Test
    void ipv6UniqueLocalPeerCountsAsInternal() {
        assertEquals("2001:db8::1", resolver(1).resolve(request("fd12:3456:789a::1", "2001:db8::1")));
    }
}
