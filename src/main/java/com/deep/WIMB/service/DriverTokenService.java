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

@Service
public class DriverTokenService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String PREFIX = "driver:token:";
    private static final Duration TTL = Duration.ofHours(12); // one shift

    public String issueToken(String busNumber) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(PREFIX + token, busNumber.trim().toUpperCase(), TTL);
        return token;
    }

    /** Bus number this token belongs to, or null if missing/expired. */
    public String resolveBusNumber(String token) {
        if (token == null || token.isBlank()) return null;
        return redisTemplate.opsForValue().get(PREFIX + token);
    }

    public void revoke(String token) {
        if (token != null) redisTemplate.delete(PREFIX + token);
    }
}