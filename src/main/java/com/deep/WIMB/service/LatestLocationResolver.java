/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import com.deep.WIMB.model.Location;
import com.deep.WIMB.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * One place that answers "where is this ride right now?", used by passenger search, the live lists,
 * the admin grid and the tracking page.
 *
 *   1. Redis last location (skipped instantly while Redis is down, see RedisCircuitBreaker)
 *   2. this server's in-memory last location (always filled)
 *   -> of those two, the NEWER one wins (after an outage Redis can hold an older point than the
 *      database or memory, and a bus must never jump backwards)
 *   3. if neither has anything: the newest row in the database
 *
 * So a running bus is shown with its last known position even when Redis is unreachable.
 */
@Service
@RequiredArgsConstructor
public class LatestLocationResolver {

    private final RedisLocationService redisLocationService;
    private final LastLocationCache lastLocationCache;
    private final LocationRepository locationRepository;

    /** Newest known location of the ride, or null if nothing at all has been recorded. */
    public Location find(Long rideId) {
        Location best = newer(
                redisLocationService.getLastLocationFromRedis(rideId),
                lastLocationCache.get(rideId));
        if (best != null) return best;

        return locationRepository.findTopByRideIdOrderByTimestampDesc(rideId).orElse(null);
    }

    /** The newer of two locations; either may be null. On a tie the first one wins. */
    static Location newer(Location first, Location second) {
        if (first == null) return second;
        if (second == null) return first;
        return LastLocationCache.isNewer(second, first) && !LastLocationCache.isNewer(first, second)
                ? second : first;
    }
}
