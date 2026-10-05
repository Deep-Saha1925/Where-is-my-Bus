/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import com.deep.WIMB.TestFields;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RideActivityTrackerTest {

    private static RideActivityTracker tracker(long staleAfter, long autoEndAfter) {
        RideActivityTracker t = new RideActivityTracker();
        TestFields.set(t, "staleAfterSeconds", staleAfter);
        TestFields.set(t, "autoEndAfterSeconds", autoEndAfter);
        t.validate();
        return t;
    }

    @Test
    void unknownRideIsNotStaleSoARestartDoesNotHideEveryBus() {
        RideActivityTracker t = tracker(300, 1800);
        assertFalse(t.isStale(1L));
        assertFalse(t.isKnown(1L));
        assertNull(t.silentSeconds(1L));
    }

    @Test
    void justTouchedRideIsLive() {
        RideActivityTracker t = tracker(300, 1800);
        t.touch(1L);
        assertTrue(t.isKnown(1L));
        assertFalse(t.isStale(1L));
        assertTrue(t.silentSeconds(1L) <= 1);
    }

    @Test
    void becomesStaleOnlyAfterTheLimit() {
        RideActivityTracker t = tracker(300, 1800);
        long now = System.currentTimeMillis();
        t.touch(2L, now - 299_000);
        t.touch(3L, now - 301_000);
        assertFalse(t.isStale(2L), "299s of silence is still live");
        assertTrue(t.isStale(3L), "301s of silence is stale");
    }

    @Test
    void aNewLocationBringsAStaleRideBack() {
        RideActivityTracker t = tracker(300, 1800);
        t.touch(3L, System.currentTimeMillis() - 400_000);
        assertTrue(t.isStale(3L));
        t.touch(3L);
        assertFalse(t.isStale(3L));
    }

    @Test
    void seedingNeverMovesTheLastSeenTimeBackwards() {
        RideActivityTracker t = tracker(300, 1800);
        long now = System.currentTimeMillis();
        t.touch(4L, now - 10_000);
        t.touch(4L, now - 900_000);   // an older stored location must not win
        assertTrue(t.silentSeconds(4L) <= 11);
    }

    @Test
    void forgetRemovesTheRide() {
        RideActivityTracker t = tracker(300, 1800);
        t.touch(5L);
        t.forget(5L);
        assertFalse(t.isKnown(5L));
        assertNull(t.lastSeen(5L));
    }

    @Test
    void nullIdsAreIgnored() {
        RideActivityTracker t = tracker(300, 1800);
        t.touch(null);
        t.touch(null, 5L);
        t.forget(null);
        assertNull(t.lastSeen(null));
        assertFalse(t.isStale(null));
    }

    @Test
    void autoEndLimitIsReportedInMillis() {
        assertEquals(1_800_000L, tracker(300, 1800).autoEndAfterMillis());
    }

    @Test
    void unsafeSettingsAreCorrected() {
        // stale below 30s is raised to 30; auto-end not above stale becomes double the stale time
        RideActivityTracker t = tracker(5, 10);
        assertEquals(60_000L, t.autoEndAfterMillis());
    }
}
