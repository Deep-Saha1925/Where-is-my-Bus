/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.config;

import com.deep.WIMB.controller.DriverAuthController;
import com.deep.WIMB.controller.LocationController;
import com.deep.WIMB.controller.RideController;
import com.deep.WIMB.service.ActiveRideCache;
import com.deep.WIMB.service.ClientIpResolver;
import com.deep.WIMB.service.DriverAccessService;
import com.deep.WIMB.service.DriverTokenService;
import com.deep.WIMB.service.LocationService;
import com.deep.WIMB.service.LoginRateLimiter;
import com.deep.WIMB.service.RideService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Who may call what, checked against the real SecurityConfig (real filter chain, real form login).
 * Only the services behind the controllers are mocked.
 */
@WebMvcTest(controllers = {RideController.class, DriverAuthController.class, LocationController.class})
@Import(SecurityConfig.class)
class SecurityRulesIT {

    private static final String ADMIN_USER = "test-admin";                  // see src/test-extra/resources
    private static final String ADMIN_PASSWORD = "Test-Admin-Password-123!";

    @Autowired MockMvc mvc;

    @MockitoBean RideService rideService;
    @MockitoBean LocationService locationService;
    @MockitoBean DriverTokenService driverTokenService;
    @MockitoBean ActiveRideCache activeRideCache;
    @MockitoBean DriverAccessService driverAccessService;
    @MockitoBean LoginRateLimiter loginRateLimiter;
    @MockitoBean ClientIpResolver clientIpResolver;

    private int statusOf(RequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    private void assertSentToLogin(String url) throws Exception {
        MvcResult result = mvc.perform(get(url)).andReturn();
        assertEquals(302, result.getResponse().getStatus(), url + " should redirect an anonymous visitor");
        String location = result.getResponse().getRedirectedUrl();
        assertNotNull(location);
        assertTrue(location.endsWith("/login.html"), url + " redirected to " + location);
    }

    /* ─── public: passengers and drivers need no login ────────────── */

    @Test
    void passengerEndpointsArePublic() throws Exception {
        assertEquals(200, statusOf(get("/api/ride/search").param("busNumber", "test")));
        assertEquals(200, statusOf(get("/api/ride/active/all")));
        assertEquals(200, statusOf(get("/api/driver/depots")));
    }

    @Test
    void driverLoginNeedsNeitherAnAdminSessionNorACsrfToken() throws Exception {
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.5");
        when(loginRateLimiter.check(any(), any(), any())).thenReturn(new LoginRateLimiter.Verdict(false, 0));
        when(driverAccessService.isValid(any(), any())).thenReturn(false);

        int status = statusOf(post("/api/driver/verify")
                .contentType("application/json")
                .content("{\"depotName\":\"X\",\"code\":\"1\",\"busNumber\":\"B\"}"));

        assertEquals(401, status, "reaches the controller (wrong code), not blocked by security or CSRF");
    }

    /* ─── protected: admin only ───────────────────────────────────── */

    @Test
    void adminPagesAndApiDocsRedirectAnonymousVisitorsToLogin() throws Exception {
        assertSentToLogin("/admin/depots");
        assertSentToLogin("/admin/routes");
        assertSentToLogin("/admin-buses.html");
        assertSentToLogin("/swagger-ui.html");
        assertSentToLogin("/v3/api-docs");
    }

    @Test
    void everythingNotListedRequiresLogin() throws Exception {
        assertSentToLogin("/api/something-unlisted");
    }

    @Test
    void adminCanLogInAndThenPassesTheAdminRules() throws Exception {
        MvcResult login = mvc.perform(post("/do-login")
                .param("username", ADMIN_USER)
                .param("password", ADMIN_PASSWORD)).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session, "a successful login creates a session");

        int status = mvc.perform(get("/admin/depots").session(session)).andReturn().getResponse().getStatus();
        // The admin controllers are not part of this slice, so 404 is fine; what matters is that
        // security let the request through (no redirect to login, no 401/403).
        assertFalse(status == 302 || status == 401 || status == 403, "admin session was refused: " + status);
    }

    @Test
    void aWrongPasswordIsRejected() throws Exception {
        MvcResult result = mvc.perform(post("/do-login")
                .param("username", ADMIN_USER)
                .param("password", "not-the-password")).andReturn();
        String location = result.getResponse().getRedirectedUrl();
        assertNotNull(location);
        assertTrue(location.contains("/login.html?error"), "redirected to " + location);
    }
}
