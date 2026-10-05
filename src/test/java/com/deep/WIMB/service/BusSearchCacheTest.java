/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import com.deep.WIMB.dto.ActiveRideResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BusSearchCacheTest {

    private static List<ActiveRideResponse> oneRide(long id) {
        ActiveRideResponse r = new ActiveRideResponse();
        r.setRideId(id);
        List<ActiveRideResponse> list = new ArrayList<>();
        list.add(r);
        return list;
    }

    @Test
    void returnsWhatWasStored() {
        BusSearchCache cache = new BusSearchCache();
        cache.put("test", oneRide(7));
        assertNotNull(cache.get("test"));
        assertEquals(7L, cache.get("test").get(0).getRideId());
    }

    @Test
    void unknownQueryIsAMiss() {
        assertNull(new BusSearchCache().get("nothing"));
    }

    @Test
    void clearForgetsEverything() {
        BusSearchCache cache = new BusSearchCache();
        cache.put("a", oneRide(1));
        cache.put("b", oneRide(2));
        cache.clear();
        assertNull(cache.get("a"));
        assertNull(cache.get("b"));
    }

    @Test
    void laterChangesToTheOriginalListDoNotLeakIntoTheCache() {
        BusSearchCache cache = new BusSearchCache();
        List<ActiveRideResponse> original = oneRide(1);
        cache.put("q", original);
        original.add(new ActiveRideResponse());
        assertEquals(1, cache.get("q").size());
    }

    @Test
    void cachedListCannotBeModifiedByCallers() {
        BusSearchCache cache = new BusSearchCache();
        cache.put("q", oneRide(1));
        List<ActiveRideResponse> cached = cache.get("q");
        assertThrows(UnsupportedOperationException.class, () -> cached.add(new ActiveRideResponse()));
    }

    @Test
    void stopsGrowingPastTheEntryLimit() {
        BusSearchCache cache = new BusSearchCache();
        for (int i = 0; i < 501; i++) cache.put("q" + i, oneRide(i));
        // hitting the limit empties the cache; the newest entry is stored afterwards
        assertNull(cache.get("q0"));
        assertNotNull(cache.get("q500"));
    }
}
