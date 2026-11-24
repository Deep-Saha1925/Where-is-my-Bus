/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.controller;

import com.deep.WIMB.model.DriverReport;
import com.deep.WIMB.repository.DriverReportRepository;
import com.deep.WIMB.service.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Passenger side of reports. Public (no login), CSRF-exempt through "/api/passenger/**"
 * in SecurityConfig. Lands in the same table as driver reports (source = PASSENGER) so the
 * admin triages everything in one "Driver Reports" tab.
 *
 * Because anyone can call it, it is guarded three ways:
 *   - per IP:   MAX_PER_IP_PER_HOUR reports an hour (in-memory, like LoginRateLimiter)
 *   - per bus:  MAX_PER_BUS_PER_HOUR passenger reports an hour about one bus number
 *   - global:   MAX_TOTAL_PER_HOUR passenger reports an hour (stops IP-rotating floods)
 * plus a hidden "website" honeypot field that real users never fill in.
 */
@RestController
@RequestMapping("/api/passenger/reports")
@RequiredArgsConstructor
public class PassengerReportController {

    private static final Set<String> TYPES = Set.of(
            "LOCATION_WRONG", "BUS_NOT_RUNNING", "CONDUCT", "APP_PROBLEM", DriverReport.OTHER);

    private static final int MAX_PER_IP_PER_HOUR = 3;
    private static final int MAX_PER_BUS_PER_HOUR = 10;
    private static final int MAX_TOTAL_PER_HOUR = 60;
    private static final int MAX_TRACKED_IPS = 20_000; // memory guard against random-key flooding
    private static final long HOUR_MS = 3_600_000L;

    /** Letters, digits, space, dash, slash, dot only (e.g. "WB-23A-1245"). */
    private static final Pattern BUS_PATTERN = Pattern.compile("^[A-Za-z0-9 ./-]{1,40}$");

    private final DriverReportRepository reportRepository;
    private final ClientIpResolver clientIpResolver;

    private final ConcurrentHashMap<String, Deque<Long>> hitsByIp = new ConcurrentHashMap<>();

    /** "website" is a honeypot: hidden in the UI, so only bots fill it in. */
    public record CreateRequest(String type, String busNumber, String place,
                                String description, String contact, String website) {}

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateRequest req, HttpServletRequest http) {
        if (req == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Empty request"));
        }

        // Bots: pretend it worked, store nothing.
        if (req.website() != null && !req.website().isBlank()) {
            return ResponseEntity.ok(Map.of("message", "Report sent to admin"));
        }

        String type = req.type() == null ? "" : req.type().trim().toUpperCase(Locale.ROOT);
        String desc = clean(req.description());
        String place = clean(req.place());
        String contact = clean(req.contact());
        String bus = clean(req.busNumber()).toUpperCase(Locale.ROOT);

        if (!TYPES.contains(type)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Pick what you are reporting"));
        }
        if (desc.length() < 5 || desc.length() > 1000) {
            return ResponseEntity.badRequest().body(Map.of("error", "Describe the problem (5 to 1000 characters)"));
        }
        if (!bus.isEmpty() && !BUS_PATTERN.matcher(bus).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Bus number looks invalid"));
        }
        if (place.length() > 150) place = place.substring(0, 150);
        if (contact.length() > 100) contact = contact.substring(0, 100);
        if (bus.isEmpty()) bus = DriverReport.NO_BUS;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime hourAgo = now.minusHours(1);

        if (tooManyFromIp(clientIpResolver.resolve(http))
                || reportRepository.countBySourceAndCreatedAtAfter(DriverReport.SOURCE_PASSENGER, hourAgo) >= MAX_TOTAL_PER_HOUR
                || (!DriverReport.NO_BUS.equals(bus)
                    && reportRepository.countBySourceAndBusNumberAndCreatedAtAfter(
                            DriverReport.SOURCE_PASSENGER, bus, hourAgo) >= MAX_PER_BUS_PER_HOUR)) {
            return ResponseEntity.status(429).body(Map.of("error", "Too many reports right now. Please try again later."));
        }

        DriverReport r = new DriverReport();
        r.setSource(DriverReport.SOURCE_PASSENGER);
        r.setBusNumber(bus);
        r.setType(type);
        r.setPlace(place.isEmpty() ? null : place);
        r.setDescription(desc);
        r.setContact(contact.isEmpty() ? null : contact);
        r.setStatus(DriverReport.NEW);
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        reportRepository.save(r);

        return ResponseEntity.ok(Map.of("message", "Report sent to admin"));
    }

    /** Trims and drops control characters (keeps normal newlines in the description). */
    private static String clean(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").trim();
    }

    /** Records this attempt and says whether the IP is over its hourly limit. */
    private boolean tooManyFromIp(String ip) {
        long now = System.currentTimeMillis();
        if (hitsByIp.size() > MAX_TRACKED_IPS) {
            hitsByIp.values().removeIf(q -> {
                synchronized (q) {
                    return q.isEmpty() || now - q.peekLast() > HOUR_MS;
                }
            });
        }
        Deque<Long> q = hitsByIp.computeIfAbsent(ip == null ? "?" : ip, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > HOUR_MS) q.pollFirst();
            if (q.size() >= MAX_PER_IP_PER_HOUR) return true;
            q.addLast(now);
            return false;
        }
    }
}