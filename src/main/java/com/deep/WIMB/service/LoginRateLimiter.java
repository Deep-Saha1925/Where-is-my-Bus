/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force protection for the driver login (POST /api/driver/verify).
 *
 * A depot code is one short shared secret, so without limits it can be guessed by trying
 * codes in a loop. Failed attempts are counted three ways, and tripping ANY of them locks
 * that key out for a while:
 *
 *   per IP            -- stops one machine hammering the endpoint
 *   per depot         -- still works if the attacker rotates IPs or fakes headers
 *   per depot + bus   -- stops guessing the code for one specific bus
 *
 * Only FAILURES count; a correct login does not reset anything, so someone holding one valid
 * code cannot "wash" their failure count by logging in between guesses.
 *
 * Trade-off: because of the per-depot limit, an attacker who keeps failing on purpose can
 * lock real drivers of that depot out for the lockout time. That is deliberate (better than
 * letting codes be guessed) and short-lived; tune the numbers in application.properties.
 *
 * In-memory and per instance, like ActiveRideCache: with several instances each would count
 * separately (move the counters to Redis at that point).
 */
@Component
public class LoginRateLimiter {

    @Value("${wimb.security.login.window-seconds:600}")
    private long windowSeconds;

    @Value("${wimb.security.login.lockout-seconds:900}")
    private long lockoutSeconds;

    @Value("${wimb.security.login.max-failures-per-ip:10}")
    private int maxPerIp;

    @Value("${wimb.security.login.max-failures-per-depot:40}")
    private int maxPerDepot;

    @Value("${wimb.security.login.max-failures-per-bus:8}")
    private int maxPerBus;

    private static final int MAX_TRACKED_KEYS = 50_000; // memory guard against random-key flooding

    /** blocked = true means "do not even check the code"; retryAfterSeconds says how long to wait. */
    public record Verdict(boolean blocked, long retryAfterSeconds) {
        static final Verdict OK = new Verdict(false, 0);
    }

    private static final class Counter {
        long windowStart;
        int failures;
        long lockedUntil;

        synchronized void recordFailure(long now, long windowMs, long lockoutMs, int max) {
            if (lockedUntil > now) return;               // already locked: don't extend it
            if (now - windowStart > windowMs) {          // old window expired: start fresh
                windowStart = now;
                failures = 0;
            }
            failures++;
            if (failures >= max) {
                lockedUntil = now + lockoutMs;
                failures = 0;
                windowStart = now;
            }
        }

        synchronized long lockedMillisLeft(long now) {
            return Math.max(0, lockedUntil - now);
        }

        synchronized boolean isIdle(long now, long windowMs) {
            return lockedUntil <= now && now - windowStart > windowMs;
        }
    }

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    /** Is this client/depot/bus currently locked out? Does not count anything. */
    public Verdict check(String ip, String depot, String bus) {
        long now = System.currentTimeMillis();
        long leftMs = Math.max(
                leftFor(ipKey(ip), now),
                Math.max(leftFor(depotKey(depot), now), leftFor(busKey(depot, bus), now)));
        return leftMs > 0 ? new Verdict(true, (leftMs + 999) / 1000) : Verdict.OK;
    }

    /** Call after a wrong depot/code. */
    public void recordFailure(String ip, String depot, String bus) {
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000;
        long lockMs = lockoutSeconds * 1000;
        count(ipKey(ip),            now, windowMs, lockMs, maxPerIp);
        count(depotKey(depot),      now, windowMs, lockMs, maxPerDepot);
        count(busKey(depot, bus),   now, windowMs, lockMs, maxPerBus);
    }

    private void count(String key, long now, long windowMs, long lockMs, int max) {
        Counter c = counters.get(key);
        if (c == null) {
            if (counters.size() >= MAX_TRACKED_KEYS) {
                purgeIdle(); // try to make room; if still full, skip tracking this key
                if (counters.size() >= MAX_TRACKED_KEYS) return;
            }
            c = counters.computeIfAbsent(key, k -> new Counter());
        }
        c.recordFailure(now, windowMs, lockMs, max);
    }

    private long leftFor(String key, long now) {
        Counter c = counters.get(key);
        return c == null ? 0 : c.lockedMillisLeft(now);
    }

    @Scheduled(fixedDelay = 300_000)
    void purgeIdle() {
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000;
        counters.entrySet().removeIf(e -> e.getValue().isIdle(now, windowMs));
    }

    private static String ipKey(String ip)                 { return "ip:" + ip; }
    private static String depotKey(String depot)           { return "depot:" + norm(depot); }
    private static String busKey(String depot, String bus) { return "bus:" + norm(depot) + "|" + norm(bus); }
    private static String norm(String s)                   { return s == null ? "" : s.trim().toUpperCase(); }
}