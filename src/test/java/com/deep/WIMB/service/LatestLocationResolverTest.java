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

/** The "which of two locations is newer" rule (the full lookup chain is tested in the extra tests). */
class LatestLocationResolverTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 6, 12, 0, 0);

    private static Location at(double lat, LocalDateTime when) {
        Location l = new Location();
        l.setLatitude(lat);
        l.setTimestamp(when);
        return l;
    }

    @Test
    void bothMissingGivesNothing() {
        assertNull(LatestLocationResolver.newer(null, null));
    }

    @Test
    void oneMissingGivesTheOther() {
        Location a = at(1, T0);
        assertEquals(1.0, LatestLocationResolver.newer(a, null).getLatitude());
        assertEquals(1.0, LatestLocationResolver.newer(null, a).getLatitude());
    }

    @Test
    void theNewerOneWinsWhicheverSideItIsOn() {
        Location older = at(1, T0);
        Location newer = at(2, T0.plusSeconds(30));
        assertEquals(2.0, LatestLocationResolver.newer(older, newer).getLatitude());
        assertEquals(2.0, LatestLocationResolver.newer(newer, older).getLatitude());
    }

    @Test
    void aTieKeepsTheFirst() {
        assertEquals(1.0, LatestLocationResolver.newer(at(1, T0), at(2, T0)).getLatitude());
    }

    @Test
    void aTimedLocationBeatsAnUntimedOne() {
        assertEquals(2.0, LatestLocationResolver.newer(at(1, null), at(2, T0)).getLatitude());
        assertEquals(2.0, LatestLocationResolver.newer(at(2, T0), at(1, null)).getLatitude());
    }
}
