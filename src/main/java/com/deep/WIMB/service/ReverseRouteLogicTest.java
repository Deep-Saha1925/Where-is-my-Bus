///*
// * Where Is My Bus (WIMB)
// * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
// * Proprietary and confidential. See the LICENSE file in the project root.
// * Unauthorized copying, modification, distribution, hosting or use is prohibited.
// */
//package com.deep.WIMB.service;
//
//import com.deep.WIMB.dto.RouteStop;
//import org.junit.jupiter.api.Test;
//
//import java.util.List;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//
//class ReverseRouteLogicTest {
//
//    private static RouteStop stop(int order, String name, double lat, double lon, double km, int slack) {
//        RouteStop s = new RouteStop();
//        s.setStopOrder(order);
//        s.setStopName(name);
//        s.setLatitude(lat);
//        s.setLongitude(lon);
//        s.setDistanceFromStartKm(km);
//        s.setSlackTimeMin(slack);
//        return s;
//    }
//
//    @Test
//    void codeIsSwappedAroundTheUnderscore() {
//        assertEquals("B_A", ReverseRouteLogic.reverseCode("A_B"));
//        assertEquals("FLK_APD", ReverseRouteLogic.reverseCode(" apd_flk "));
//    }
//
//    @Test
//    void codeThatCannotBeSwappedGetsASuffix() {
//        assertEquals("COOCH_BEHAR_APD_RETURN", ReverseRouteLogic.reverseCode("COOCH_BEHAR_APD"));
//        assertEquals("APDFLK_RETURN", ReverseRouteLogic.reverseCode("APDFLK"));
//    }
//
//    @Test
//    void nameKeepsItsSeparator() {
//        assertEquals("Falakata \u2192 Alipurduar", ReverseRouteLogic.reverseName("Alipurduar \u2192 Falakata", "x", "y"));
//        assertEquals("Falakata \u21C4 Alipurduar", ReverseRouteLogic.reverseName("Alipurduar \u21C4 Falakata", "x", "y"));
//        assertEquals("Cooch Behar to Alipurduar", ReverseRouteLogic.reverseName("Alipurduar to Cooch Behar", "x", "y"));
//        assertEquals("CoochBehar_Alipurduar", ReverseRouteLogic.reverseName("Alipurduar_CoochBehar", "x", "y"));
//    }
//
//    @Test
//    void nameDoesNotSplitInsideAWord() {
//        assertEquals("Falakata \u2192 Toto Para", ReverseRouteLogic.reverseName("Toto Para \u2192 Falakata", "x", "y"));
//    }
//
//    @Test
//    void nameWithoutSeparatorFallsBackToTheDepots() {
//        assertEquals("FALAKATA \u2192 ALIPURDUAR", ReverseRouteLogic.reverseName("Express 12", "ALIPURDUAR", "FALAKATA"));
//    }
//
//    @Test
//    void stopsAreReversedAndDistancesRecalculated() {
//        List<RouteStop> forward = List.of(
//                stop(1, "Alipurduar", 26.4799, 89.5355, 0.0, 0),
//                stop(2, "Chowpathy", 26.48083, 89.526, 1.5, 2),
//                stop(3, "Sonapur", 26.494, 89.368, 10.5, 3),
//                stop(4, "Falakata", 26.51721, 89.20694, 15.0, 0));
//
//        List<RouteStop> back = ReverseRouteLogic.reverseStops(forward);
//
//        assertEquals(List.of("Falakata", "Sonapur", "Chowpathy", "Alipurduar"),
//                back.stream().map(RouteStop::getStopName).toList());
//        assertEquals(List.of(1, 2, 3, 4), back.stream().map(RouteStop::getStopOrder).toList());
//        assertEquals(List.of(0.0, 4.5, 13.5, 15.0), back.stream().map(RouteStop::getDistanceFromStartKm).toList());
//        assertEquals(List.of(0, 3, 2, 0), back.stream().map(RouteStop::getSlackTimeMin).toList());
//        assertEquals(26.51721, back.get(0).getLatitude());
//        assertEquals("Alipurduar", forward.get(0).getStopName());   // the input is not modified
//    }
//
//    @Test
//    void distancesNotStartingAtZeroStillStartAtZeroOnTheWayBack() {
//        List<RouteStop> forward = List.of(
//                stop(1, "A", 1, 1, 5.0, 0), stop(2, "M", 2, 2, 9.0, 1), stop(3, "B", 3, 3, 20.0, 0));
//        assertEquals(List.of(0.0, 11.0, 15.0),
//                ReverseRouteLogic.reverseStops(forward).stream().map(RouteStop::getDistanceFromStartKm).toList());
//    }
//
//    @Test
//    void reversingTwiceGivesTheOriginalDistances() {
//        List<RouteStop> forward = List.of(
//                stop(1, "A", 1, 1, 0.0, 0), stop(2, "M", 2, 2, 1.5, 2), stop(3, "B", 3, 3, 15.0, 0));
//        assertEquals(List.of(0.0, 1.5, 15.0), ReverseRouteLogic.reverseStops(ReverseRouteLogic.reverseStops(forward))
//                .stream().map(RouteStop::getDistanceFromStartKm).toList());
//    }
//}
