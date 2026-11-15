/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.controller;

import com.deep.WIMB.model.DriverReport;
import com.deep.WIMB.model.Notice;
import com.deep.WIMB.repository.DriverReportRepository;
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
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private static final Set<String> STATUSES = Set.of(
            DriverReport.NEW, DriverReport.UNDER_REVIEW, DriverReport.NEED_INFO,
            DriverReport.APPROVED, DriverReport.REJECTED);
    private static final Set<String> SEVERITIES = Set.of(Notice.INFO, Notice.WARNING, Notice.CRITICAL);

    private final DriverReportRepository reportRepository;
    private final NoticeRepository noticeRepository;

    @GetMapping
    public List<DriverReport> list(@RequestParam(value = "status", required = false) String status) {
        if (status == null || status.isBlank()) return reportRepository.findTop200ByOrderByCreatedAtDesc();
        return reportRepository.findTop200ByStatusOrderByCreatedAtDesc(status.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * Move a report along the workflow, save an internal note, and/or write a reply for the driver.
     * adminReply: omitted = leave the current reply alone, blank = remove it, text = set it.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestParam("status") String status,
                                    @RequestParam(value = "adminNote", required = false) String adminNote,
                                    @RequestParam(value = "adminReply", required = false) String adminReply) {
        DriverReport r = reportRepository.findById(id).orElse(null);
        if (r == null) return ResponseEntity.status(404).body(Map.of("error", "Report not found"));

        String st = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(st)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid status"));
        }
        String note = adminNote == null ? "" : adminNote.trim();
        if (note.length() > 500) {
            return ResponseEntity.badRequest().body(Map.of("error", "Note is too long (max 500 characters)"));
        }

        // Reply for the driver (validated before anything is changed)
        String reply = adminReply == null ? null : adminReply.trim();
        if (reply != null) {
            if (reply.length() > 500) {
                return ResponseEntity.badRequest().body(Map.of("error", "Reply is too long (max 500 characters)"));
            }
            if (!reply.isEmpty() && DriverReport.SOURCE_PASSENGER.equals(r.getSource())) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "Passenger reports cannot get a reply (a passenger has no account to read it)"));
            }
        }

        LocalDateTime now = LocalDateTime.now();
        r.setStatus(st);
        r.setAdminNote(note.isEmpty() ? null : note);
        if (reply != null) {
            String current = r.getAdminReply() == null ? "" : r.getAdminReply();
            if (!reply.equals(current)) {              // only a real change counts as a new reply
                r.setAdminReply(reply.isEmpty() ? null : reply);
                r.setRepliedAt(reply.isEmpty() ? null : now);
            }
        }
        r.setUpdatedAt(now);
        reportRepository.save(r);
        return ResponseEntity.ok(Map.of("message", "Report updated"));
    }

    /**
     * Turn a report into a public notice (the driver's text is only a draft:
     * the admin edits title/message before confirming). Marks the report APPROVED.
     */
    @PostMapping("/{id}/publish")
    public ResponseEntity<?> publish(@PathVariable Long id,
                                     @RequestParam("title") String title,
                                     @RequestParam("message") String message,
                                     @RequestParam(value = "severity", defaultValue = "INFO") String severity,
                                     @RequestParam(value = "expiresInHours", required = false) Integer expiresInHours) {
        DriverReport r = reportRepository.findById(id).orElse(null);
        if (r == null) return ResponseEntity.status(404).body(Map.of("error", "Report not found"));
        if (r.getPublishedNoticeId() != null) {
            return ResponseEntity.status(409).body(Map.of("error", "Already published as a notice"));
        }

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
        // passenger reports that are not about one bus carry the "-" placeholder
        n.setBusNumber(DriverReport.NO_BUS.equals(r.getBusNumber()) ? null : r.getBusNumber());
        n.setActive(true);
        n.setCreatedAt(now);
        n.setExpiresAt(expiresInHours == null ? null : now.plusHours(expiresInHours));
        noticeRepository.save(n);

        r.setPublishedNoticeId(n.getId());
        r.setStatus(DriverReport.APPROVED);
        r.setUpdatedAt(now);
        reportRepository.save(r);

        return ResponseEntity.ok(Map.of("message", "Published as a passenger notice", "noticeId", n.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!reportRepository.existsById(id)) {
            return ResponseEntity.status(404).body(Map.of("error", "Report not found"));
        }
        reportRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Report deleted"));
    }
}