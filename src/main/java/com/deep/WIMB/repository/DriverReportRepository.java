/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.repository;

import com.deep.WIMB.model.DriverReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface DriverReportRepository extends JpaRepository<DriverReport, Long> {

    List<DriverReport> findTop200ByOrderByCreatedAtDesc();

    List<DriverReport> findTop200ByStatusOrderByCreatedAtDesc(String status);

    List<DriverReport> findTop20ByBusNumberOrderByCreatedAtDesc(String busNumber);

    long countByBusNumberAndCreatedAtAfter(String busNumber, LocalDateTime after);

    long countByStatus(String status);
}
