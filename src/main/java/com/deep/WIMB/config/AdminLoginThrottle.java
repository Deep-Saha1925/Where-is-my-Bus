/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.config;

import com.deep.WIMB.service.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force protection for the ADMIN login (POST /do-login). The driver login
 * already had a rate limiter; the admin login -- the most valuable target -- had
 * none, so a password could be guessed at full speed.
 *
 * After MAX_FAILURES wrong passwords from one IP inside WINDOW_MS, that IP is
 * locked out for LOCK_MS. While locked, even the correct password is refused.
 */
@Component
public class AdminLoginThrottle extends OncePerRequestFilter {

    private static final int  MAX_FAILURES = 5;
    private static final long WINDOW_MS    = 15 * 60_000L;
    private static final long LOCK_MS      = 15 * 60_000L;
    private static final int  MAX_TRACKED  = 10_000;

    private record Entry(int failures, long windowStart, long lockedUntil) {}

    private final ConcurrentHashMap<String, Entry> byIp = new ConcurrentHashMap<>();
    private final ClientIpResolver ipResolver;

    public AdminLoginThrottle(ClientIpResolver ipResolver) {
        this.ipResolver = ipResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod()) && "/do-login".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Entry e = byIp.get(ipResolver.resolve(request));
        if (e != null && e.lockedUntil() > System.currentTimeMillis()) {
            // Looks like a normal failed login to the page, but never reaches the password check.
            response.sendRedirect(request.getContextPath() + "/login.html?error=true");
            return;
        }
        chain.doFilter(request, response);
    }

    public void recordFailure(HttpServletRequest request) {
        long now = System.currentTimeMillis();
        if (byIp.size() >= MAX_TRACKED) {
            byIp.entrySet().removeIf(en -> en.getValue().lockedUntil() < now && now - en.getValue().windowStart() > WINDOW_MS);
            if (byIp.size() >= MAX_TRACKED) byIp.clear();
        }
        byIp.merge(ipResolver.resolve(request), new Entry(1, now, 0), (old, ignored) -> {
            int count = (now - old.windowStart() > WINDOW_MS) ? 1 : old.failures() + 1;
            long start = (now - old.windowStart() > WINDOW_MS) ? now : old.windowStart();
            return new Entry(count, start, count >= MAX_FAILURES ? now + LOCK_MS : 0);
        });
    }

    public void reset(HttpServletRequest request) {
        byIp.remove(ipResolver.resolve(request));
    }
}
