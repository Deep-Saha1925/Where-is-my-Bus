/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import com.deep.WIMB.model.Location;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LastLocationCacheTest {

    private static Location at(double lat, LocalDateTime when) {
        Location l = new Location();
        l.setLatitude(lat);
        l.setTimestamp(when);
        return l;
    }

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 6, 12, 0, 0);

    @Test
    void remembersTheLocationOfARide() {
        LastLocationCache cache = new LastLocationCache();
        cache.put(1L, at(26.1, T0));
        assertEquals(26.1, cache.get(1L).getLatitude());
        assertNull(cache.get(2L));
    }

    @Test
    void aNewerLocationReplacesAnOlderOne() {
        LastLocationCache cache = new LastLocationCache();
        cache.put(1L, at(26.1, T0));
        cache.put(1L, at(26.2, T0.plusSeconds(10)));
        assertEquals(26.2, cache.get(1L).getLatitude());
    }

    @Test
    void anOlderLocationNeverReplacesANewerOne() {
        LastLocationCache cache = new LastLocationCache();
        cache.put(1L, at(26.2, T0.plusSeconds(10)));
        cache.put(1L, at(26.1, T0));
        assertEquals(26.2, cache.get(1L).getLatitude());
    }

    @Test
    void sameTimestampTakesTheLaterArrival() {
        LastLocationCache cache = new LastLocationCache();
        cache.put(1L, at(26.1, T0));
        cache.put(1L, at(26.3, T0));
        assertEquals(26.3, cache.get(1L).getLatitude());
    }

    @Test
    void aLocationWithoutATimeIsKeptWhenNothingElseIsStoredButNeverBeatsARealOne() {
        LastLocationCache cache = new LastLocationCache();
        cache.put(1L, at(26.9, null));
        assertEquals(26.9, cache.get(1L).getLatitude());

        cache.put(1L, at(26.1, T0));
        assertEquals(26.1, cache.get(1L).getLatitude(), "a timed location beats an untimed one");

        cache.put(1L, at(26.9, null));
        assertEquals(26.1, cache.get(1L).getLatitude(), "an untimed one never replaces a timed one");
    }

    @Test
    void removeForgetsTheRide() {
        LastLocationCache cache = new LastLocationCache();
        cache.put(1L, at(26.1, T0));
        cache.remove(1L);
        assertNull(cache.get(1L));
    }

    @Test
    void nullsAreIgnored() {
        LastLocationCache cache = new LastLocationCache();
        cache.put(null, at(26.1, T0));
        cache.put(1L, null);
        cache.remove(null);
        assertNull(cache.get(null));
        assertNull(cache.get(1L));
    }

    @Test
    void isNewerTreatsAnUnknownTimeAsOldest() {
        assertTrue(LastLocationCache.isNewer(at(0, T0), at(0, null)));
        assertTrue(LastLocationCache.isNewer(at(0, T0), at(0, T0)));
    }
}
