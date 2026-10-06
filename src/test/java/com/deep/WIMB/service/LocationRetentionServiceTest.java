/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import com.deep.WIMB.TestFields;
import com.deep.WIMB.repository.LocationRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationRetentionServiceTest {

    /** A repository that "deletes" from a pretend pile of old rows and records every call. */
    static class FakeRepo {
        int rowsLeft;
        final List<LocalDateTime> cutoffs = new ArrayList<>();
        final List<Integer> batchSizes = new ArrayList<>();
        RuntimeException failWith;

        FakeRepo(int rows) { this.rowsLeft = rows; }

        LocationRepository proxy() {
            return (LocationRepository) Proxy.newProxyInstance(
                    LocationRepository.class.getClassLoader(),
                    new Class<?>[]{LocationRepository.class},
                    (p, method, args) -> {
                        if (!method.getName().equals("deleteOldBatch")) {
                            throw new UnsupportedOperationException(method.getName());
                        }
                        if (failWith != null) throw failWith;
                        cutoffs.add((LocalDateTime) args[0]);
                        int batch = (Integer) args[1];
                        batchSizes.add(batch);
                        int deleted = Math.min(batch, rowsLeft);
                        rowsLeft -= deleted;
                        return deleted;
                    });
        }
    }

    private static LocationRetentionService service(FakeRepo repo, int days, int batch, int maxBatches) {
        LocationRetentionService s = new LocationRetentionService(repo.proxy());
        TestFields.set(s, "retentionDays", days);
        TestFields.set(s, "batchSize", batch);
        TestFields.set(s, "maxBatchesPerRun", maxBatches);
        return s;
    }

    @Test
    void zeroDaysSwitchesCleanupOffAndNeverTouchesTheDatabase() {
        FakeRepo repo = new FakeRepo(10_000);
        assertEquals(0, service(repo, 0, 1000, 10).purgeOldLocations());
        assertEquals(0, service(repo, -5, 1000, 10).purgeOldLocations());
        assertTrue(repo.cutoffs.isEmpty());
        assertEquals(10_000, repo.rowsLeft);
    }

    @Test
    void cutoffIsExactlyTheRetentionPeriodBeforeNow() {
        FakeRepo repo = new FakeRepo(0);
        LocalDateTime now = LocalDateTime.of(2026, 10, 6, 3, 30);
        service(repo, 30, 1000, 10).purgeOldLocations(now);
        assertEquals(LocalDateTime.of(2026, 9, 6, 3, 30), repo.cutoffs.get(0));
    }

    @Test
    void keepsDeletingBatchesUntilAShortBatchSaysItIsDone() {
        FakeRepo repo = new FakeRepo(2500);
        int deleted = service(repo, 30, 1000, 50).purgeOldLocations();
        assertEquals(2500, deleted);
        assertEquals(List.of(1000, 1000, 1000), repo.batchSizes, "1000 + 1000 + 500 (short batch ends the loop)");
        assertEquals(0, repo.rowsLeft);
    }

    @Test
    void anExactMultipleCostsOneExtraCheckThenStops() {
        FakeRepo repo = new FakeRepo(2000);
        assertEquals(2000, service(repo, 30, 1000, 50).purgeOldLocations());
        assertEquals(3, repo.batchSizes.size(), "1000 + 1000 + 0");
    }

    @Test
    void oneRunNeverDeletesMoreThanMaxBatchesTimesBatchSize() {
        FakeRepo repo = new FakeRepo(1_000_000);
        int deleted = service(repo, 30, 1000, 3).purgeOldLocations();
        assertEquals(3000, deleted);
        assertEquals(997_000, repo.rowsLeft, "the rest is left for the next run");
    }

    @Test
    void absurdSettingsAreCorrected() {
        FakeRepo repo = new FakeRepo(5);
        service(repo, 30, 1, 0).purgeOldLocations();   // batch 1 -> raised to 100, maxBatches 0 -> 1
        assertEquals(List.of(100), repo.batchSizes);
    }

    @Test
    void aDatabaseErrorIsPassedOnSoTheSchedulerCanLogIt() {
        FakeRepo repo = new FakeRepo(10);
        repo.failWith = new IllegalStateException("db down");
        assertThrows(IllegalStateException.class, () -> service(repo, 30, 1000, 10).purgeOldLocations());
    }
}
