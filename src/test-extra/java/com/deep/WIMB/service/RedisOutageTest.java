/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import com.deep.WIMB.model.Location;
import com.deep.WIMB.repository.LocationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What happens when Redis is down: drivers can still log in and send locations, and the newest known
 * position of a running bus is still found (memory first, the database as last resort).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisOutageTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 6, 12, 0, 0);

    private static Location at(double lat, LocalDateTime when) {
        Location l = new Location();
        l.setLatitude(lat);
        l.setTimestamp(when);
        return l;
    }

    /* ─── driver tokens ───────────────────────────────────────────── */

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Spy RedisCircuitBreaker circuit = new RedisCircuitBreaker();
    @InjectMocks DriverTokenService tokens;

    @Test
    void driverCanLogInAndHisTokenWorksWhileRedisIsDown() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis is down"));

        String token = tokens.issueToken("wb-1");

        assertNotNull(token);
        assertEquals("WB-1", tokens.resolveBusNumber(token), "token is found from memory, no Redis needed");
    }

    @Test
    void anUnknownTokenIsRejectedWithoutAnErrorWhileRedisIsDown() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis is down"));
        assertNull(tokens.resolveBusNumber("never-issued"));
    }

    @Test
    void afterOneFailureRedisIsNotCalledAgainDuringTheCooldown() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis is down"));

        tokens.issueToken("WB-1");          // first call hits Redis and fails
        tokens.resolveBusNumber("x");       // skipped
        tokens.resolveBusNumber("y");       // skipped
        tokens.issueToken("WB-2");          // skipped

        verify(redisTemplate, times(1)).opsForValue();
    }

    @Test
    void whenRedisWorksTheTokenIsStoredThereToo() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        String token = tokens.issueToken("wb-1");
        verify(valueOps).set(any(String.class), any(String.class), any(java.time.Duration.class));
        assertEquals("WB-1", tokens.resolveBusNumber(token));
    }

    @Test
    void aTokenOnlyRedisKnowsIsFoundAndRemembered() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("driver:token:old-token")).thenReturn("WB-9");

        assertEquals("WB-9", tokens.resolveBusNumber("old-token"));
        assertEquals("WB-9", tokens.resolveBusNumber("old-token"));
        verify(valueOps, times(1)).get("driver:token:old-token");   // the second answer came from memory
    }

    /* ─── last known location ─────────────────────────────────────── */

    @Mock RedisLocationService redisLocationService;
    @Mock LocationRepository locationRepository;
    LastLocationCache memory = new LastLocationCache();

    private LatestLocationResolver resolver() {
        return new LatestLocationResolver(redisLocationService, memory, locationRepository);
    }

    @Test
    void redisDownMemoryAnswers() {
        when(redisLocationService.getLastLocationFromRedis(1L)).thenReturn(null);   // what it returns while down
        memory.put(1L, at(26.5, T0));

        assertEquals(26.5, resolver().find(1L).getLatitude());
        verify(locationRepository, never()).findTopByRideIdOrderByTimestampDesc(any());
    }

    @Test
    void theNewerOfRedisAndMemoryWins() {
        when(redisLocationService.getLastLocationFromRedis(1L)).thenReturn(at(26.1, T0));
        memory.put(1L, at(26.2, T0.plusSeconds(20)));      // saved during an outage, Redis never saw it

        assertEquals(26.2, resolver().find(1L).getLatitude(), "the bus must not jump backwards");
    }

    @Test
    void afterARestartTheDatabaseIsTheLastResort() {
        when(redisLocationService.getLastLocationFromRedis(1L)).thenReturn(null);
        when(locationRepository.findTopByRideIdOrderByTimestampDesc(1L)).thenReturn(Optional.of(at(26.7, T0)));

        assertEquals(26.7, resolver().find(1L).getLatitude());
    }

    @Test
    void nothingAnywhereGivesNull() {
        when(redisLocationService.getLastLocationFromRedis(1L)).thenReturn(null);
        when(locationRepository.findTopByRideIdOrderByTimestampDesc(1L)).thenReturn(Optional.empty());

        assertNull(resolver().find(1L));
    }
}
