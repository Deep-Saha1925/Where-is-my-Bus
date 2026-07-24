package com.deep.WIMB.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class DriverTokenService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String PREFIX = "driver:token:";
    private static final Duration TTL = Duration.ofHours(12); // one shift



}
