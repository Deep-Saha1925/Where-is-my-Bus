/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.controller;

import com.deep.WIMB.dto.UpcomingBus;
import com.deep.WIMB.service.UpcomingBusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Public, read-only. Lives under /api/routes/** so SecurityConfig already allows it
 * (no change needed there).
 */
@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class UpcomingBusController {

    private static final int MAX_NAME = 100;
    private static final int MAX_LIVE_ROUTES = 30;

    private final UpcomingBusService upcomingBusService;

    /**
     * Scheduled buses coming soon between two stops.
     * liveRoutes = comma-separated route codes that already have a live bus for this search;
     * those routes are not shown as "late" (the bus is evidently running).
     */
    @GetMapping("/upcoming")
    public List<UpcomingBus> upcoming(@RequestParam String source,
                                      @RequestParam String destination,
                                      @RequestParam(required = false) String liveRoutes) {
        String src = source == null ? "" : source.trim();
        String dst = destination == null ? "" : destination.trim();
        if (src.isEmpty() || dst.isEmpty() || src.length() > MAX_NAME || dst.length() > MAX_NAME) {
            return List.of();
        }
        Set<String> live = liveRoutes == null ? Set.of()
                : Arrays.stream(liveRoutes.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty() && s.length() <= 60)
                        .limit(MAX_LIVE_ROUTES)
                        .collect(Collectors.toSet());
        return upcomingBusService.suggest(src, dst, live);
    }
}
