/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.controller;

import com.deep.WIMB.service.DepotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.function.Supplier;

/** Admin-only (/admin/** is ROLE_ADMIN): which buses belong to which depot (used by the Depot Codes tab). */
@RestController
@RequestMapping("/admin/depot-buses")
@RequiredArgsConstructor
public class AdminDepotBusController {

    private final DepotService depotService;

    @GetMapping
    public Map<String, ?> list() {
        return depotService.busesByDepot();
    }

    @PostMapping("/assign")
    public ResponseEntity<?> assign(@RequestParam String depot, @RequestParam String busNumbers) {
        return run(() -> depotService.assignBuses(depot, busNumbers));
    }

    @PostMapping("/unassign")
    public ResponseEntity<?> unassign(@RequestParam String depot, @RequestParam String busNumber) {
        return run(() -> {
            depotService.unassignBus(depot, busNumber);
            return Map.of("message", "Bus removed from depot");
        });
    }

    private ResponseEntity<?> run(Supplier<Object> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", String.valueOf(e.getReason())));
        }
    }
}
