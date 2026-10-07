/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Driver login tokens. Stored in Redis (shared, survives a restart) AND in this server's memory.
 *
 * Redis alone was a single point of failure: with Redis down a driver could not log in at all,
 * and every location update was rejected because the token could not be looked up. No updates
 * meant the bus went silent and vanished from search, even though everything else still worked.
 * Now the memory copy is always written and checked first (it is also faster: no Redis call for
 * every GPS update), and Redis is a best-effort second copy that survives restarts.
 *
 * A token issued while Redis is down exists only in memory, so after a server restart in that
 * same outage the driver logs in again. Single instance only (a second instance would need Redis).
 */
@Service
public class DriverTokenService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisCircuitBreaker redisCircuit;

    private static final String PREFIX = "driver:token:";
    private static final Duration TTL = Duration.ofHours(12); // one shift
    private static final long LOCAL_COPY_OF_REDIS_TOKEN_MS = Duration.ofHours(1).toMillis();
    private static final int MAX_LOCAL_TOKENS = 10_000;

    private record LocalToken(String busNumber, long expiresAt) {}

    private final ConcurrentHashMap<String, LocalToken> local = new ConcurrentHashMap<>();

    public String issueToken(String busNumber) {
        String bus = busNumber.trim().toUpperCase();
        String token = UUID.randomUUID().toString();

        remember(token, bus, TTL.toMillis());            // always works

        if (redisCircuit.allowCall()) {                  // best effort, survives a restart
            try {
                redisTemplate.opsForValue().set(PREFIX + token, bus, TTL);
                redisCircuit.recordSuccess();
            } catch (Exception redisUnavailable) {
                redisCircuit.recordFailure(redisUnavailable);
            }
        }
        return token;
    }

    /** Bus number this token belongs to, or null if missing/expired. Never throws because Redis is down. */
    public String resolveBusNumber(String token) {
        if (token == null || token.isBlank()) return null;

        LocalToken known = local.get(token);
        if (known != null) {
            if (known.expiresAt() > System.currentTimeMillis()) return known.busNumber();
            local.remove(token, known);
        }

        if (!redisCircuit.allowCall()) return null;
        try {
            String bus = redisTemplate.opsForValue().get(PREFIX + token);
            redisCircuit.recordSuccess();
            if (bus != null) {
                remember(token, bus, LOCAL_COPY_OF_REDIS_TOKEN_MS); // e.g. issued before a restart
            }
            return bus;
        } catch (Exception redisUnavailable) {
            redisCircuit.recordFailure(redisUnavailable);
            return null;
        }
    }

    public void revoke(String token) {
        if (token == null) return;
        local.remove(token);
        if (!redisCircuit.allowCall()) return;
        try {
            redisTemplate.delete(PREFIX + token);
            redisCircuit.recordSuccess();
        } catch (Exception redisUnavailable) {
            redisCircuit.recordFailure(redisUnavailable);
        }
    }

    private void remember(String token, String bus, long ttlMillis) {
        if (local.size() >= MAX_LOCAL_TOKENS) {
            long now = System.currentTimeMillis();
            local.entrySet().removeIf(e -> e.getValue().expiresAt() <= now);
            if (local.size() >= MAX_LOCAL_TOKENS) local.clear(); // runaway growth guard; drivers just log in again
        }
        local.put(token, new LocalToken(bus, System.currentTimeMillis() + ttlMillis));
    }
}
