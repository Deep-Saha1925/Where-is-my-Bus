/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import com.deep.WIMB.config.InputRules;
import com.deep.WIMB.enums.RideStatus;
import com.deep.WIMB.model.Bus;
import com.deep.WIMB.model.Depot;
import com.deep.WIMB.model.Location;
import com.deep.WIMB.model.Ride;
import com.deep.WIMB.repository.BusRepository;
import com.deep.WIMB.repository.DepotRepository;
import com.deep.WIMB.repository.RideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Everything the depot dashboard shows when a bus card is clicked: current trip, last known
 * position and the bus's recent trips.
 *
 * Isolation rule (same as {@link DepotService}): a bus is only returned if it belongs to the
 * depot that is asking. For any other bus, or an unknown one, the result is empty, so a depot
 * can never read another depot's buses by guessing a number.
 */
@Service
@RequiredArgsConstructor
public class DepotBusDetailService {

    private final DepotRepository depotRepository;
    private final BusRepository busRepository;
    private final RideRepository rideRepository;
    private final LatestLocationResolver latestLocationResolver;
    private final RideActivityTracker activityTracker;
    private final RouteExcelLoader routeExcelLoader;

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> detail(String depotName, String rawBusNumber) {
        String number;
        try {
            number = InputRules.busNumber(rawBusNumber);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        String name = depotName == null ? "" : depotName.trim().toUpperCase(Locale.ROOT);

        Optional<Depot> depot = depotRepository.findByName(name);
        if (depot.isEmpty()) return Optional.empty();

        Optional<Bus> found = busRepository.findByBusNumber(number);
        if (found.isEmpty()) return Optional.empty();
        Bus bus = found.get();
        if (bus.getDepot() == null || !bus.getDepot().getId().equals(depot.get().getId())) {
            return Optional.empty();
        }

        List<Ride> trips = rideRepository.findTop10ByBusOrderByStartTimeDesc(bus);
        Ride active = trips.stream()
                .filter(r -> r.getStatus() == RideStatus.ACTIVE)
                .findFirst().orElse(null);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("busNumber", bus.getBusNumber());
        out.put("depot", depot.get().getName());
        out.put("running", active != null);

        if (active != null) {
            String[] ends = ends(active);
            out.put("rideId", active.getId());
            out.put("source", ends[0]);
            out.put("destination", ends[1]);
            out.put("startedAt", epoch(active.getStartTime()));
            out.put("stale", activityTracker.isStale(active.getId()));
            out.put("silentSeconds", activityTracker.silentSeconds(active.getId()));

            Location loc = latestLocationResolver.find(active.getId());
            if (loc != null) {
                out.put("latitude", loc.getLatitude());
                out.put("longitude", loc.getLongitude());
                out.put("lastUpdate", epoch(loc.getTimestamp()));
            }
        }

        List<Map<String, Object>> recent = new ArrayList<>();
        for (Ride r : trips) {
            String[] ends = ends(r);
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("source", ends[0]);
            t.put("destination", ends[1]);
            t.put("startedAt", epoch(r.getStartTime()));
            t.put("endedAt", epoch(r.getEndTime()));
            t.put("active", r.getStatus() == RideStatus.ACTIVE);
            recent.add(t);
        }
        out.put("recentTrips", recent);
        return Optional.of(out);
    }

    /** {source, destination} of a ride; both null if the route key can't be split. */
    private String[] ends(Ride ride) {
        try {
            String code = routeExcelLoader.resolveRouteCode(ride.getRouteCode());
            String[] parts = routeExcelLoader.splitRouteKey(code, ride.getRouteKey());
            if (parts != null && parts.length >= 2) return new String[]{parts[0], parts[1]};
        } catch (RuntimeException ignored) {
            // an old ride on a route that no longer exists: show it without names
        }
        return new String[]{null, null};
    }

    private static Long epoch(LocalDateTime t) {
        return t == null ? null : t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
