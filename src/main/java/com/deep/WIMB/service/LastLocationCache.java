/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import com.deep.WIMB.model.Location;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The newest location of every running ride, kept in this server's memory.
 *
 * It is filled on every location update and when a ride starts, whether or not Redis works. That is
 * what lets passengers and admins keep seeing running buses during a Redis outage even when the
 * newest points were only ever in Redis and have not been copied to the database yet (that copy
 * happens every 10 minutes).
 *
 * Lost on restart (the lookup then falls back to Redis and the database). Single instance only.
 */
@Component
public class LastLocationCache {

    private final ConcurrentHashMap<Long, Location> latest = new ConcurrentHashMap<>();

    /** Remembers this location unless a newer one is already stored. */
    public void put(Long rideId, Location location) {
        if (rideId == null || location == null) return;
        latest.merge(rideId, location, (current, incoming) -> isNewer(incoming, current) ? incoming : current);
    }

    public Location get(Long rideId) {
        return rideId == null ? null : latest.get(rideId);
    }

    public void remove(Long rideId) {
        if (rideId != null) latest.remove(rideId);
    }

    /** True when {@code a} is at least as new as {@code b}; an unknown time counts as oldest. */
    static boolean isNewer(Location a, Location b) {
        LocalDateTime ta = a == null ? null : a.getTimestamp();
        LocalDateTime tb = b == null ? null : b.getTimestamp();
        if (ta == null) return tb == null && a != null;
        if (tb == null) return true;
        return !ta.isBefore(tb);
    }
}
