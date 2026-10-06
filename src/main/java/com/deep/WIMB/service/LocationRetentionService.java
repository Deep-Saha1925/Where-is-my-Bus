/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import com.deep.WIMB.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Keeps the location table from growing forever.
 *
 * Every GPS point ever sent ends up in the location table (Redis only holds recent ones). One
 * bus sending every 10 seconds adds thousands of rows a day, and the free database tier is small.
 * Passengers only ever need the CURRENT position, so rows older than the retention period are
 * deleted, in small batches, and never for a ride that is still ACTIVE.
 *
 * Settings (application.properties / environment):
 *   LOCATION_RETENTION_DAYS   how many days of history to keep (default 30; 0 turns clean-up off)
 *   RETENTION_BATCH_SIZE      rows deleted per statement (default 5000)
 *   RETENTION_MAX_BATCHES     most statements per run (default 200, so one run deletes at most
 *                             batch-size x max-batches rows; the next night continues)
 */
@Service
@RequiredArgsConstructor
public class LocationRetentionService {

    private static final int MIN_BATCH_SIZE = 100;

    private final LocationRepository locationRepository;

    @Value("${wimb.retention.location-days:30}")
    private int retentionDays;

    @Value("${wimb.retention.batch-size:5000}")
    private int batchSize;

    @Value("${wimb.retention.max-batches-per-run:200}")
    private int maxBatchesPerRun;

    /** Deletes old locations. Returns how many rows were removed (0 when clean-up is switched off). */
    public int purgeOldLocations() {
        return purgeOldLocations(LocalDateTime.now());
    }

    // package-private so a test can pass a fixed "now"
    int purgeOldLocations(LocalDateTime now) {
        if (retentionDays <= 0) {
            return 0; // clean-up disabled
        }

        LocalDateTime cutoff = now.minusDays(retentionDays);
        int batch = Math.max(MIN_BATCH_SIZE, batchSize);
        int maxBatches = Math.max(1, maxBatchesPerRun);

        int total = 0;
        for (int i = 0; i < maxBatches; i++) {
            int deleted = locationRepository.deleteOldBatch(cutoff, batch);
            total += deleted;
            if (deleted < batch) {
                break; // nothing (or the last bit) left
            }
        }
        return total;
    }
}
