/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.controller;

import com.deep.WIMB.config.InputRules;
import com.deep.WIMB.model.Bus;
import com.deep.WIMB.model.Depot;
import com.deep.WIMB.model.DriverReport;
import com.deep.WIMB.repository.BusRepository;
import com.deep.WIMB.repository.DepotRepository;
import com.deep.WIMB.repository.DriverReportRepository;
import com.deep.WIMB.service.DepotService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Depot side of reports / requests to the admin ("add this route", "fix this route", "new stop",
 * "bus problem"...). Covered by the /depot/** rule in SecurityConfig (depot login required, CSRF
 * token required). The depot's identity always comes from its login, never from the request body.
 * Requests land in the same table as driver and passenger reports (source = DEPOT), so the admin
 * handles everything in the one "Reports" tab and can reply, change the status or publish a notice.
 */
@RestController
@RequestMapping("/depot/api/reports")
@RequiredArgsConstructor
public class DepotReportController {

    private static final Set<String> TYPES = Set.of(
            DriverReport.ROUTE_REQUEST, DriverReport.ROUTE_CORRECTION, DriverReport.STOP_REQUEST,
            DriverReport.BUS_PROBLEM, DriverReport.OTHER);
    private static final int MAX_PER_HOUR = 10;

    private final DriverReportRepository reportRepository;
    private final DepotRepository depotRepository;
    private final BusRepository busRepository;

    /** busNumber is optional: only for requests about one of the depot's own buses. */
    public record CreateRequest(String type, String busNumber, String place, String description) {}

    /**
     * What a depot may see about its own requests: status and the reply the admin wrote for it.
     * The admin's internal note (adminNote) is never included.
     */
    public record DepotView(Long id, String type, String busNumber, String place, String description,
                            String status, String createdAt, String updatedAt,
                            String adminReply, String repliedAt) {}

    private static String depotOf(Authentication auth) {
        return auth.getName().substring(DepotService.LOGIN_PREFIX.length());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateRequest req, Authentication auth) {
        if (req == null) return ResponseEntity.badRequest().body(Map.of("error", "Empty request"));
        String depotName = depotOf(auth);

        String type = req.type() == null ? "" : req.type().trim().toUpperCase(Locale.ROOT);
        String desc = req.description() == null ? "" : req.description().trim();
        String place = req.place() == null ? "" : req.place().trim();

        if (!TYPES.contains(type)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Pick what you want to report or request"));
        }
        if (desc.length() < 10 || desc.length() > 1000) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please describe it (10 to 1000 characters)"));
        }
        if (place.length() > 150) place = place.substring(0, 150);

        // Optional bus: must be one of THIS depot's buses.
        String bus = DriverReport.NO_BUS;
        String rawBus = req.busNumber() == null ? "" : req.busNumber().trim();
        if (!rawBus.isEmpty()) {
            Optional<Bus> found = Optional.empty();
            Optional<Depot> depot = depotRepository.findByName(depotName);
            try {
                if (depot.isPresent()) found = busRepository.findByBusNumber(InputRules.busNumber(rawBus));
            } catch (RuntimeException ignored) {
                // invalid bus number text: handled below as "not in this depot"
            }
            boolean mine = found.isPresent() && found.get().getDepot() != null
                    && found.get().getDepot().getId().equals(depot.get().getId());
            if (!mine) {
                return ResponseEntity.badRequest().body(Map.of("error", "That bus is not in your depot"));
            }
            bus = found.get().getBusNumber();
        }

        LocalDateTime now = LocalDateTime.now();
        if (reportRepository.countByDepotNameAndSourceAndCreatedAtAfter(
                depotName, DriverReport.SOURCE_DEPOT, now.minusHours(1)) >= MAX_PER_HOUR) {
            return ResponseEntity.status(429).body(Map.of("error", "Too many requests in the last hour. Please wait."));
        }

        DriverReport r = new DriverReport();
        r.setSource(DriverReport.SOURCE_DEPOT);
        r.setDepotName(depotName);
        r.setBusNumber(bus);
        r.setType(type);
        r.setPlace(place.isEmpty() ? null : place);
        r.setDescription(desc);
        r.setStatus(DriverReport.NEW);
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        reportRepository.save(r);

        return ResponseEntity.ok(Map.of("message", "Sent to admin", "id", r.getId()));
    }

    /** This depot's own requests, newest first. */
    @GetMapping
    public List<DepotView> mine(Authentication auth) {
        return reportRepository.findOwnByDepot(depotOf(auth), PageRequest.of(0, 30)).stream()
                .map(r -> new DepotView(r.getId(), r.getType(),
                        DriverReport.NO_BUS.equals(r.getBusNumber()) ? null : r.getBusNumber(),
                        r.getPlace(), r.getDescription(), r.getStatus(),
                        DriverReport.toInstant(r.getCreatedAt()), DriverReport.toInstant(r.getUpdatedAt()),
                        r.getAdminReply(), DriverReport.toInstant(r.getRepliedAt())))
                .toList();
    }
}
