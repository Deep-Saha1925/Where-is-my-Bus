/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.controller;

import com.deep.WIMB.model.DriverReport;
import com.deep.WIMB.repository.DriverReportRepository;
import com.deep.WIMB.service.DriverTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Driver side of reports. Already public + CSRF-exempt through the existing
 * /api/driver/** rules; identity comes from the X-Driver-Token header
 * (same mechanism the ride endpoints use).
 */
@RestController
@RequestMapping("/api/driver/reports")
@RequiredArgsConstructor
public class DriverReportController {

    private static final Set<String> TYPES = Set.of(
            DriverReport.BREAKDOWN, DriverReport.ROAD_BLOCKAGE, DriverReport.ROUTE_REQUEST, DriverReport.OTHER);
    private static final int MAX_PER_HOUR = 5;

    private final DriverReportRepository reportRepository;
    private final DriverTokenService driverTokenService;

    public record CreateRequest(String type, String place, String description) {}

    /** What a driver may see: status only, never the admin's internal note. */
    public record DriverView(Long id, String type, String place, String description,
                             String status, String createdAt, String updatedAt) {}

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateRequest req,
                                    @RequestHeader(value = "X-Driver-Token", required = false) String token) {
        String bus = driverTokenService.resolveBusNumber(token);
        if (bus == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Please log in again"));
        }

        String type = req.type() == null ? "" : req.type().trim().toUpperCase(Locale.ROOT);
        String desc = req.description() == null ? "" : req.description().trim();
        String place = req.place() == null ? "" : req.place().trim();

        if (!TYPES.contains(type)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Pick what you are reporting"));
        }
        if (desc.length() < 5 || desc.length() > 1000) {
            return ResponseEntity.badRequest().body(Map.of("error", "Describe the problem (5 to 1000 characters)"));
        }
        if (place.length() > 150) place = place.substring(0, 150);

        LocalDateTime now = LocalDateTime.now();
        if (reportRepository.countDriverReportsSince(bus, now.minusHours(1)) >= MAX_PER_HOUR) {
            return ResponseEntity.status(429).body(Map.of("error", "Too many reports in the last hour. Please wait."));
        }

        DriverReport r = new DriverReport();
        r.setBusNumber(bus);
        r.setSource(DriverReport.SOURCE_DRIVER);
        r.setType(type);
        r.setPlace(place.isEmpty() ? null : place);
        r.setDescription(desc);
        r.setStatus(DriverReport.NEW);
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        reportRepository.save(r);

        return ResponseEntity.ok(Map.of("message", "Report sent to admin", "id", r.getId()));
    }

    /** The logged-in bus's own recent reports, newest first. */
    @GetMapping
    public ResponseEntity<?> mine(@RequestHeader(value = "X-Driver-Token", required = false) String token) {
        String bus = driverTokenService.resolveBusNumber(token);
        if (bus == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Please log in again"));
        }
        List<DriverView> out = reportRepository.findOwnByBus(bus, PageRequest.of(0, 20)).stream()
                .map(r -> new DriverView(r.getId(), r.getType(), r.getPlace(), r.getDescription(),
                        r.getStatus(), DriverReport.toInstant(r.getCreatedAt()),
                        DriverReport.toInstant(r.getUpdatedAt())))
                .toList();
        return ResponseEntity.ok(out);
    }
}
