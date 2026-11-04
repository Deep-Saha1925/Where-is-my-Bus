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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** Public, read-only: what the index page banner polls. */
@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeRepository noticeRepository;

    /** Only the fields a passenger needs - no active flag, no expiry internals. */
    public record NoticeView(Long id, String title, String message, String severity,
                             String routeCode, String busNumber, LocalDateTime createdAt) {}

    private static int rank(String severity) {
        return switch (severity == null ? "" : severity) {
            case Notice.CRITICAL -> 0;
            case Notice.WARNING -> 1;
            default -> 2;
        };
    }

    @GetMapping("/active")
    public List<NoticeView> active() {
        LocalDateTime now = LocalDateTime.now();
        return noticeRepository.findByActiveTrueOrderByCreatedAtDesc().stream()
                .filter(n -> n.isLive(now))
                // most severe first; within a severity the newest stays first (stable sort)
                .sorted(Comparator.comparingInt(n -> rank(n.getSeverity())))
                .map(n -> new NoticeView(n.getId(), n.getTitle(), n.getMessage(), n.getSeverity(),
                        n.getRouteCode(), n.getBusNumber(), n.getCreatedAt()))
                .toList();
    }
}
