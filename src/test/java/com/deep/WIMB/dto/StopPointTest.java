/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.dto;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StopPointTest {

    private static RouteStop stop(String name, double lat, double lng) {
        RouteStop s = new RouteStop();
        s.setStopName(name);
        s.setLatitude(lat);
        s.setLongitude(lng);
        return s;
    }

    private static List<List<RouteStop>> routes(List<RouteStop>... lists) {
        return new ArrayList<>(List.of(lists));
    }

    @Test
    void aStopSharedByTwoRoutesAppearsOnceWithTheFirstRoutesCoordinates() {
        List<StopPoint> points = StopPoint.uniqueByName(routes(
                List.of(stop("Falakata", 26.52, 89.20), stop("Alipurduar", 26.49, 89.52)),
                List.of(stop("FALAKATA", 99.0, 99.0), stop("Cooch Behar", 26.32, 89.45))));

        assertEquals(3, points.size());
        StopPoint falakata = points.stream().filter(p -> p.name().equalsIgnoreCase("falakata")).findFirst().orElseThrow();
        assertEquals(26.52, falakata.latitude());
        assertEquals("Falakata", falakata.name());
    }

    @Test
    void resultIsSortedByNameIgnoringCase() {
        List<StopPoint> points = StopPoint.uniqueByName(routes(
                List.of(stop("zebra", 1, 1), stop("Alpha", 2, 2), stop("beta", 3, 3))));
        assertEquals(List.of("Alpha", "beta", "zebra"), points.stream().map(StopPoint::name).toList());
    }

    @Test
    void stopsWithoutANameOrWithoutRealCoordinatesAreSkipped() {
        List<StopPoint> points = StopPoint.uniqueByName(routes(List.of(
                stop("  ", 26, 89),
                stop(null, 26, 89),
                stop("Zero", 0.0, 0.0),
                stop("NotANumber", Double.NaN, 89),
                stop("OutOfRange", 120, 89),
                stop("Good", 26.1, 89.1))));
        assertEquals(1, points.size());
        assertEquals("Good", points.get(0).name());
    }

    @Test
    void namesAreTrimmed() {
        List<StopPoint> points = StopPoint.uniqueByName(routes(List.of(stop("  Sonapur  ", 26.5, 89.6))));
        assertEquals("Sonapur", points.get(0).name());
    }

    @Test
    void noRoutesGivesAnEmptyList() {
        assertTrue(StopPoint.uniqueByName(new ArrayList<List<RouteStop>>()).isEmpty());
    }
}
