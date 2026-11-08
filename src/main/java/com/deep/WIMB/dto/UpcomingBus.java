/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.dto;

import java.util.List;

/**
 * One scheduled (not live) bus the passenger can wait for, worked out from a route's
 * admin-entered departure times. Times are "HH:mm" in the server's schedule time zone.
 *
 * status:
 *   DUE       leaves the passenger's stop within about 5 minutes
 *   UPCOMING  leaves later than that
 *   LATE      its scheduled time has passed but no live ride is on this route
 *             (not started, delayed, or the driver is not using the app)
 */
public record UpcomingBus(
        String routeCode,
        String routeName,
        String status,
        String startStop,        // where the timetable's departure time applies (the route's first stop)
        String departsStart,     // timetable departure from startStop
        String atSource,         // estimated time at the passenger's source stop
        String atDestination,    // estimated arrival at the passenger's destination
        long minutesUntil,       // from now to atSource; negative = already past
        boolean nextDay,         // atSource is tomorrow
        boolean approximate,     // atSource is an estimate (the stop is not the route's first stop)
        int stopsBetween,
        double distanceKm,
        List<String> busNumbers  // roster of buses on this route (may be empty; not tied to a time)
) {}
