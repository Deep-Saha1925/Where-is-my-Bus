/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.scheduler;

import com.deep.WIMB.service.LocationRetentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the location clean-up once a night, at a quiet time (default 03:30 India time). */
@Component
@RequiredArgsConstructor
@Slf4j
public class LocationRetentionScheduler {

    private final LocationRetentionService retentionService;

    @Scheduled(cron = "${wimb.retention.cron:0 30 3 * * *}", zone = "${wimb.retention.zone:Asia/Kolkata}")
    public void purgeOldLocations() {
        try {
            int deleted = retentionService.purgeOldLocations();
            if (deleted > 0) {
                log.info("Location clean-up removed {} old row(s)", deleted);
            }
        } catch (Exception e) {
            // Never let one failed night stop the schedule; it simply tries again tomorrow
            log.error("Location clean-up failed: {}", e.getMessage(), e);
        }
    }
}
