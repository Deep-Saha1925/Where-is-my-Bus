/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers WHEN each active ride last reported a location, in memory (no DB or Redis call).
 *
 * This is what finds "ghost buses": a driver who closes the tab, loses signal or runs out of
 * battery never ends the ride, so it stays ACTIVE and kept showing up for passengers.
 *
 *   silent for more than stale-after-seconds      -> hidden from passenger search and lists
 *                                                    (it comes back by itself if the driver's
 *                                                    location starts arriving again)
 *   silent for more than auto-end-after-seconds   -> the ride is ended by StaleRideScheduler
 *
 * Updated on every location update (LocationService) and when a ride starts. After a restart
 * the map is empty; RideService#seedUnknownRideActivity refills it from the newest stored
 * location, and a ride with no known time is treated as live until then (so a restart never
 * hides every bus at once).
 *
 * Single instance only, like ActiveRideCache.
 */
@Service
public class RideActivityTracker {

    @Value("${wimb.ride.stale-after-seconds:300}")
    private long staleAfterSeconds;

    @Value("${wimb.ride.auto-end-after-seconds:1800}")
    private long autoEndAfterSeconds;

    private final ConcurrentHashMap<Long, Long> lastSeenMillis = new ConcurrentHashMap<>();

    @PostConstruct
    void validate() {
        if (staleAfterSeconds < 30) {
            System.err.println("wimb.ride.stale-after-seconds=" + staleAfterSeconds
                    + " is very low (drivers send about every 10s); using 30 instead.");
            staleAfterSeconds = 30;
        }
        if (autoEndAfterSeconds <= staleAfterSeconds) {
            long fixed = staleAfterSeconds * 2;
            System.err.println("wimb.ride.auto-end-after-seconds (" + autoEndAfterSeconds
                    + ") must be larger than stale-after-seconds (" + staleAfterSeconds
                    + "); using " + fixed + " instead.");
            autoEndAfterSeconds = fixed;
        }
    }

    /** A location just arrived for this ride. */
    public void touch(Long rideId) {
        if (rideId != null) lastSeenMillis.put(rideId, System.currentTimeMillis());
    }

    /** Records a known past time (used when refilling after a restart). Never moves time backwards. */
    public void touch(Long rideId, long epochMillis) {
        if (rideId != null) lastSeenMillis.merge(rideId, epochMillis, Math::max);
    }

    public void forget(Long rideId) {
        if (rideId != null) lastSeenMillis.remove(rideId);
    }

    public boolean isKnown(Long rideId) {
        return rideId != null && lastSeenMillis.containsKey(rideId);
    }

    /** Epoch millis of the last location, or null if unknown. */
    public Long lastSeen(Long rideId) {
        return rideId == null ? null : lastSeenMillis.get(rideId);
    }

    /** Seconds since the last location, or null if unknown. */
    public Long silentSeconds(Long rideId) {
        Long last = lastSeen(rideId);
        return last == null ? null : Math.max(0, (System.currentTimeMillis() - last) / 1000);
    }

    /** True once a ride has been silent longer than stale-after-seconds. Unknown = not stale (grace). */
    public boolean isStale(Long rideId) {
        Long silent = silentSeconds(rideId);
        return silent != null && silent > staleAfterSeconds;
    }

    public long autoEndAfterMillis() {
        return autoEndAfterSeconds * 1000;
    }
}