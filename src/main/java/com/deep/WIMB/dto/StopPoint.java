/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.dto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A bus stop with its coordinates. The passenger page downloads the list of these once and finds
 * the stop nearest to the passenger IN THE BROWSER, so the passenger's own location is never sent
 * to the server.
 */
public record StopPoint(String name, double latitude, double longitude) {

    /**
     * One entry per stop name (case-insensitive) across every route; a junction shared by several
     * routes appears once, with the coordinates of the first route that lists it. Stops without a
     * name or without real coordinates (blank, NaN, or exactly 0,0) are skipped. Sorted by name.
     */
    public static List<StopPoint> uniqueByName(Collection<? extends Collection<RouteStop>> routes) {
        Map<String, StopPoint> byName = new LinkedHashMap<>();
        for (Collection<RouteStop> stops : routes) {
            for (RouteStop stop : stops) {
                String name = stop.getStopName() == null ? "" : stop.getStopName().trim();
                if (name.isEmpty()) continue;
                if (!hasRealCoordinates(stop.getLatitude(), stop.getLongitude())) continue;
                byName.putIfAbsent(name.toUpperCase(), new StopPoint(name, stop.getLatitude(), stop.getLongitude()));
            }
        }
        List<StopPoint> result = new ArrayList<>(byName.values());
        result.sort(Comparator.comparing(StopPoint::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static boolean hasRealCoordinates(double lat, double lng) {
        if (Double.isNaN(lat) || Double.isNaN(lng) || Double.isInfinite(lat) || Double.isInfinite(lng)) return false;
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return false;
        return !(lat == 0.0 && lng == 0.0);
    }
}
