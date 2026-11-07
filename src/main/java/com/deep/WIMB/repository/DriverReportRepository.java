/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.repository;

import com.deep.WIMB.model.DriverReport;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DriverReportRepository extends JpaRepository<DriverReport, Long> {

    List<DriverReport> findTop200ByOrderByCreatedAtDesc();

    List<DriverReport> findTop200ByStatusOrderByCreatedAtDesc(String status);

    /** A driver's own reports only (passenger reports about the same bus are NOT included). */
    @Query("select r from DriverReport r where r.busNumber = :bus "
            + "and (r.source is null or r.source = 'DRIVER') order by r.createdAt desc")
    List<DriverReport> findOwnByBus(@Param("bus") String bus, Pageable pageable);

    /** Driver rate limit: passenger reports must not use up a driver's hourly quota. */
    @Query("select count(r) from DriverReport r where r.busNumber = :bus and r.createdAt > :after "
            + "and (r.source is null or r.source = 'DRIVER')")
    long countDriverReportsSince(@Param("bus") String bus, @Param("after") LocalDateTime after);

    long countBySourceAndCreatedAtAfter(String source, LocalDateTime after);

    long countBySourceAndBusNumberAndCreatedAtAfter(String source, String busNumber, LocalDateTime after);

    long countByStatus(String status);
}
