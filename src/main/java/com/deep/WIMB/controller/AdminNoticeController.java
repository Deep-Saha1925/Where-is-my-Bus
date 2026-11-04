/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.controller;

import com.deep.WIMB.model.Notice;
import com.deep.WIMB.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Admin-only (covered by the /admin/** rule in SecurityConfig). */
@RestController
@RequestMapping("/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController {

    private static final Set<String> SEVERITIES = Set.of(Notice.INFO, Notice.WARNING, Notice.CRITICAL);

    private final NoticeRepository noticeRepository;

    @GetMapping
    public List<Notice> list() {
        return noticeRepository.findAllByOrderByCreatedAtDesc();
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestParam("title") String title,
            @RequestParam("message") String message,
            @RequestParam(value = "severity", defaultValue = "INFO") String severity,
            @RequestParam(value = "routeCode", required = false) String routeCode,
            @RequestParam(value = "busNumber", required = false) String busNumber,
            @RequestParam(value = "expiresInHours", required = false) Integer expiresInHours
    ) {
        String t = title == null ? "" : title.trim();
        String m = message == null ? "" : message.trim();
        String sev = severity == null ? "" : severity.trim().toUpperCase(Locale.ROOT);

        if (t.isEmpty() || t.length() > 100) {
            return ResponseEntity.badRequest().body(Map.of("error", "Title is required (max 100 characters)"));
        }
        if (m.isEmpty() || m.length() > 500) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message is required (max 500 characters)"));
        }
        if (!SEVERITIES.contains(sev)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Severity must be INFO, WARNING or CRITICAL"));
        }
        if (expiresInHours != null && (expiresInHours < 1 || expiresInHours > 24 * 90)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Expiry must be between 1 hour and 90 days"));
        }

        LocalDateTime now = LocalDateTime.now();
        Notice n = new Notice();
        n.setTitle(t);
        n.setMessage(m);
        n.setSeverity(sev);
        n.setRouteCode(blankToNull(routeCode, 40, true));
        n.setBusNumber(blankToNull(busNumber, 40, true));
        n.setActive(true);
        n.setCreatedAt(now);
        // Relative hours (not a clock time) so the admin's and server's time zones never disagree.
        n.setExpiresAt(expiresInHours == null ? null : now.plusHours(expiresInHours));
        noticeRepository.save(n);

        return ResponseEntity.ok(Map.of("message", "Notice published", "id", n.getId()));
    }

    /** Switch a notice on/off without losing it. */
    @PutMapping("/{id}/active")
    public ResponseEntity<?> setActive(@PathVariable Long id, @RequestParam("value") boolean value) {
        Notice n = noticeRepository.findById(id).orElse(null);
        if (n == null) return ResponseEntity.status(404).body(Map.of("error", "Notice not found"));
        n.setActive(value);
        noticeRepository.save(n);
        return ResponseEntity.ok(Map.of("message", value ? "Notice is live again" : "Notice hidden"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!noticeRepository.existsById(id)) {
            return ResponseEntity.status(404).body(Map.of("error", "Notice not found"));
        }
        noticeRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Notice deleted"));
    }

    private static String blankToNull(String raw, int max, boolean upper) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isEmpty()) return null;
        if (v.length() > max) v = v.substring(0, max);
        return upper ? v.toUpperCase(Locale.ROOT) : v;
    }
}
