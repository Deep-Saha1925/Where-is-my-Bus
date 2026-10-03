/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.scheduler;

import com.deep.WIMB.service.RideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ends rides whose driver stopped sending locations ("ghost buses").
 * See RideActivityTracker for the two time limits.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StaleRideScheduler {

    private final RideService rideService;

    /** After a restart, work out when each running ride was last heard from. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            rideService.seedUnknownRideActivity();
        } catch (Exception e) {
            log.error("Could not seed ride activity at startup (non-fatal): {}", e.getMessage());
        }
    }

    @Scheduled(initialDelay = 30_000, fixedDelayString = "${wimb.ride.cleanup-interval-ms:60000}")
    public void endStaleRides() {
        try {
            int ended = rideService.endStaleRides();
            if (ended > 0) {
                log.warn("Auto-ended {} ride(s) that stopped sending location", ended);
            }
        } catch (Exception e) {
            // Never let one bad run kill the schedule
            log.error("Stale ride cleanup failed: {}", e.getMessage(), e);
        }
    }
}