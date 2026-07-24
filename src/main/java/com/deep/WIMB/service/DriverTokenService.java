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

    public String issueToken(String busNumber){
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(PREFIX + token, busNumber.trim().toUpperCase(), TTL);
        return token;
    }

    /** Returns the bus number this token was issued for, or null if invalid/expired. */
    public String resolveBusNumber(String token) {
        if (token == null) return null;
        return redisTemplate.opsForValue().get(PREFIX + token);
    }

}
