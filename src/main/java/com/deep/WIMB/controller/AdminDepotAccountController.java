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

import java.util.Map;
import java.util.function.Supplier;

/** Admin-only (/admin/** is ROLE_ADMIN): create depot logins and assign buses to them. */
@RestController
@RequestMapping("/admin/depot-accounts/api")
@RequiredArgsConstructor
public class AdminDepotAccountController {

    private final DepotService depotService;

    @GetMapping
    public Object list() {
        return depotService.listForAdmin();
    }

    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestParam String name, @RequestParam String username,
                                    @RequestParam String password) {
        return run(() -> {
            depotService.create(name, username, password);
            return Map.of("message", "Depot created");
        });
    }

    @PostMapping("/{id}/password")
    public ResponseEntity<?> password(@PathVariable Long id, @RequestParam String password) {
        return run(() -> {
            depotService.setPassword(id, password);
            return Map.of("message", "Password updated");
        });
    }

    @PostMapping("/{id}/enabled")
    public ResponseEntity<?> enabled(@PathVariable Long id, @RequestParam boolean enabled) {
        return run(() -> {
            depotService.setEnabled(id, enabled);
            return Map.of("message", enabled ? "Depot enabled" : "Depot disabled");
        });
    }

    @PostMapping("/{id}/buses")
    public ResponseEntity<?> assign(@PathVariable Long id, @RequestParam String busNumbers) {
        return run(() -> depotService.assignBuses(id, busNumbers));
    }

    @DeleteMapping("/{id}/buses")
    public ResponseEntity<?> unassign(@PathVariable Long id, @RequestParam String busNumber) {
        return run(() -> {
            depotService.unassignBus(id, busNumber);
            return Map.of("message", "Bus removed from depot");
        });
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        return run(() -> {
            depotService.delete(id);
            return Map.of("message", "Depot deleted (its buses are now unassigned)");
        });
    }

    private ResponseEntity<?> run(Supplier<Object> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", String.valueOf(e.getReason())));
        }
    }
}
