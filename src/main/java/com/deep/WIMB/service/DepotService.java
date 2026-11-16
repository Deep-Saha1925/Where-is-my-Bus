/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import com.deep.WIMB.config.InputRules;
import com.deep.WIMB.dto.ActiveRideResponse;
import com.deep.WIMB.model.Bus;
import com.deep.WIMB.model.Depot;
import com.deep.WIMB.repository.BusRepository;
import com.deep.WIMB.repository.DepotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Depot accounts and the buses they own.
 *
 * Isolation rule: everything a depot user can read goes through {@link #liveBuses(Depot)},
 * which only ever looks at buses where bus.depot == that depot.
 */
@Service
@RequiredArgsConstructor
public class DepotService {

    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9._-]{3,30}$");

    private final DepotRepository depotRepository;
    private final BusRepository busRepository;
    private final RideService rideService;
    private final PasswordEncoder passwordEncoder;

    @Value("${wimb.admin.username}")
    private String adminUsername;

    // ───────────── admin side ─────────────

    @Transactional
    public Depot create(String name, String username, String password) {
        String n = InputRules.safeText(name, "name", 60).toUpperCase(Locale.ROOT);
        String u = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(u).matches()) {
            throw new IllegalArgumentException("Username: 3-30 chars, letters, digits, dot, dash or underscore");
        }
        if (u.equalsIgnoreCase(adminUsername)) {
            throw new IllegalArgumentException("That username is reserved");
        }
        checkPassword(password);
        if (depotRepository.existsByName(n)) throw new IllegalArgumentException("A depot named " + n + " already exists");
        if (depotRepository.existsByUsername(u)) throw new IllegalArgumentException("Username already taken");

        Depot d = new Depot();
        d.setName(n);
        d.setUsername(u);
        d.setPasswordHash(passwordEncoder.encode(password));
        return depotRepository.save(d);
    }

    @Transactional
    public void setPassword(Long id, String password) {
        checkPassword(password);
        Depot d = get(id);
        d.setPasswordHash(passwordEncoder.encode(password));
        depotRepository.save(d);
    }

    @Transactional
    public void setEnabled(Long id, boolean enabled) {
        Depot d = get(id);
        d.setEnabled(enabled);
        depotRepository.save(d);
    }

    /** Deleting a depot keeps its buses; they just become unassigned. */
    @Transactional
    public void delete(Long id) {
        Depot d = get(id);
        List<Bus> buses = busRepository.findByDepotOrderByBusNumber(d);
        buses.forEach(b -> b.setDepot(null));
        busRepository.saveAll(buses);
        depotRepository.delete(d);
    }

    /**
     * Assigns buses (comma / newline separated) to a depot, creating the bus row if it has
     * never run yet. A bus that already belongs to ANOTHER depot is skipped and reported,
     * never silently moved.
     */
    @Transactional
    public Map<String, Object> assignBuses(Long id, String rawList) {
        Depot d = get(id);
        List<String> added = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        List<String> invalid = new ArrayList<>();

        for (String token : (rawList == null ? "" : rawList).split("[,\\n\\r;]+")) {
            if (token.isBlank()) continue;
            String number;
            try {
                number = InputRules.busNumber(token);
            } catch (RuntimeException e) {
                invalid.add(token.trim());
                continue;
            }
            Bus bus = busRepository.findByBusNumber(number).orElseGet(() -> {
                Bus b = new Bus();
                b.setBusNumber(number);
                return b;
            });
            if (bus.getDepot() != null && !bus.getDepot().getId().equals(d.getId())) {
                conflicts.add(number + " (belongs to " + bus.getDepot().getName() + ")");
                continue;
            }
            bus.setDepot(d);
            busRepository.save(bus);
            added.add(number);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("added", added);
        out.put("conflicts", conflicts);
        out.put("invalid", invalid);
        return out;
    }

    @Transactional
    public void unassignBus(Long id, String busNumber) {
        Depot d = get(id);
        Bus bus = busRepository.findByBusNumber(InputRules.busNumber(busNumber))
                .orElseThrow(() -> new IllegalArgumentException("Bus not found"));
        if (bus.getDepot() == null || !bus.getDepot().getId().equals(d.getId())) {
            throw new IllegalArgumentException("Bus does not belong to this depot");
        }
        bus.setDepot(null);
        busRepository.save(bus);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForAdmin() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Depot d : depotRepository.findAll()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", d.getId());
            row.put("name", d.getName());
            row.put("username", d.getUsername());
            row.put("enabled", d.isEnabled());
            row.put("buses", busRepository.findByDepotOrderByBusNumber(d).stream()
                    .map(Bus::getBusNumber).collect(Collectors.toList()));
            out.add(row);
        }
        out.sort(Comparator.comparing(m -> (String) m.get("name")));
        return out;
    }

    // ───────────── depot side (scoped) ─────────────

    @Transactional(readOnly = true)
    public Depot findByUsername(String username) {
        return depotRepository.findByUsername(username == null ? "" : username.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("Unknown depot"));
    }

    /** This depot's buses only, each marked running or not. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> liveBuses(Depot depot) {
        List<String> own = busRepository.findByDepotOrderByBusNumber(depot).stream()
                .map(Bus::getBusNumber).collect(Collectors.toList());

        Map<String, ActiveRideResponse> running = new HashMap<>();
        for (ActiveRideResponse r : rideService.getAllActiveRides()) {
            if (r.getBusNumber() != null) running.put(r.getBusNumber(), r);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (String number : own) {
            ActiveRideResponse r = running.get(number);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("busNumber", number);
            row.put("running", r != null);
            if (r != null) {
                row.put("rideId", r.getRideId());
                row.put("source", r.getSourceName());
                row.put("destination", r.getDestinationName());
                row.put("stale", r.getStale());
                row.put("silentSeconds", r.getSilentSeconds());
                row.put("remainingDistanceKm", r.getRemainingDistanceKm());
            }
            out.add(row);
        }
        return out;
    }

    // ───────────── helpers ─────────────

    private Depot get(Long id) {
        return depotRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Depot not found"));
    }

    private static void checkPassword(String p) {
        if (p == null || p.length() < 8 || p.length() > 72) {
            throw new IllegalArgumentException("Password must be 8-72 characters");
        }
    }
}
