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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Which buses belong to which depot, and the depot's own (scoped) live view.
 * Depots are the names in the admin's Depot Codes list; login password = that depot's code.
 *
 * Isolation rule: everything a depot user can read goes through {@link #liveBuses(String)},
 * which only ever looks at buses where bus.depot == that depot.
 */
@Service
@RequiredArgsConstructor
public class DepotService {

    /** Prefix the login page adds to the username when "Depot" is chosen. */
    public static final String LOGIN_PREFIX = "depot:";

    private final DepotRepository depotRepository;
    private final BusRepository busRepository;
    private final RideService rideService;
    private final DriverAccessService driverAccessService;

    // ───────────── admin side ─────────────

    /** depot name -> its bus numbers, for every depot in the codes list. */
    @Transactional(readOnly = true)
    public Map<String, List<String>> busesByDepot() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String name : driverAccessService.getDepotNames()) {
            List<String> buses = depotRepository.findByName(name)
                    .map(d -> busRepository.findByDepotOrderByBusNumber(d).stream()
                            .map(Bus::getBusNumber).collect(Collectors.toList()))
                    .orElseGet(ArrayList::new);
            out.put(name, buses);
        }
        return out;
    }

    /**
     * Assigns buses (comma / newline separated) to a depot, creating the bus row if it has
     * never run yet. A bus that already belongs to ANOTHER depot is skipped and reported,
     * never silently moved.
     */
    @Transactional
    public Map<String, Object> assignBuses(String depotName, String rawList) {
        Depot d = getOrCreate(depotName);
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

    /**
     * Called when a driver logs in with a valid depot name + code. A bus that has no depot yet
     * is attached to that depot, so it shows up on the depot dashboard without the admin having
     * to pre-assign every bus by hand. A bus that already belongs to the same depot is left alone,
     * and a bus that belongs to ANOTHER depot is never moved (same rule as assignBuses).
     *
     * @return true if the bus now belongs to this depot
     */
    @Transactional
    public boolean claimBusForLogin(String depotName, String busNumber) {
        Depot d = getOrCreate(depotName);
        String number = InputRules.busNumber(busNumber);
        Bus bus = busRepository.findByBusNumber(number).orElseGet(() -> {
            Bus b = new Bus();
            b.setBusNumber(number);
            return b;
        });
        if (bus.getDepot() == null) {
            bus.setDepot(d);
            busRepository.save(bus);
            return true;
        }
        return bus.getDepot().getId().equals(d.getId());
    }

    @Transactional
    public void unassignBus(String depotName, String busNumber) {
        Depot d = getOrCreate(depotName);
        Bus bus = busRepository.findByBusNumber(InputRules.busNumber(busNumber))
                .orElseThrow(() -> new IllegalArgumentException("Bus not found"));
        if (bus.getDepot() == null || !bus.getDepot().getId().equals(d.getId())) {
            throw new IllegalArgumentException("Bus does not belong to this depot");
        }
        bus.setDepot(null);
        busRepository.save(bus);
    }

    // ───────────── depot side (scoped) ─────────────

    /** This depot's buses only, each marked running or not. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> liveBuses(String depotName) {
        Optional<Depot> depot = depotRepository.findByName(normalize(depotName));
        if (depot.isEmpty()) return new ArrayList<>();

        List<String> own = busRepository.findByDepotOrderByBusNumber(depot.get()).stream()
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

    private Depot getOrCreate(String depotName) {
        String name = normalize(depotName);
        if (!driverAccessService.getAllDepotCodes().containsKey(name)) {
            throw new IllegalArgumentException("Unknown depot: " + depotName);
        }
        return depotRepository.findByName(name).orElseGet(() -> {
            Depot d = new Depot();
            d.setName(name);
            return depotRepository.save(d);
        });
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
    }
}