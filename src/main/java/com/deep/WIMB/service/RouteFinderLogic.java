/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import com.deep.WIMB.dto.RouteStop;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** The pure rules behind the Route Finder: matching a search to routes, and checking edited stops. */
final class RouteFinderLogic {

    private RouteFinderLogic() {}

    static final int MAX_STOPS = 300;
    private static final int MAX_LIST_ITEMS = 60;

    enum Kind { DIRECT, VIA, OPPOSITE, TOUCHES }

    /** fromIndex / toIndex are positions in the sheet (0-based) or -1 when unknown. */
    record Match(Kind kind, int fromIndex, int toIndex) {}

    /** "Falakata" matches "Falakata Bus Stand" and "falakata"; spaces, case and punctuation don't matter. */
    static boolean matches(String query, String candidate) {
        String q = ReverseRouteLogic.normalize(query);
        String c = ReverseRouteLogic.normalize(candidate);
        if (q.isEmpty() || c.isEmpty()) return false;
        return c.contains(q) || (c.length() >= 4 && q.contains(c));
    }

    /** First stop that equals the query, else the first stop that merely contains it; -1 if none. */
    static int indexOfStop(List<RouteStop> stops, String query) {
        String q = ReverseRouteLogic.normalize(query);
        if (q.isEmpty() || stops == null) return -1;
        for (int i = 0; i < stops.size(); i++) {
            if (ReverseRouteLogic.normalize(stops.get(i).getStopName()).equals(q)) return i;
        }
        for (int i = 0; i < stops.size(); i++) {
            if (matches(query, stops.get(i).getStopName())) return i;
        }
        return -1;
    }

    /**
     * How a route relates to "from -> to", or null if it doesn't.
     *   DIRECT    registered as from -> to
     *   VIA       its stops contain from, then to (the route passes through both, in that order)
     *   OPPOSITE  its stops contain both but in the other order (the way back is what runs)
     *   TOUCHES   only one of the two was asked for and the route serves it
     */
    static Match match(String from, String to, String source, String destination, List<RouteStop> stops) {
        boolean hasFrom = !ReverseRouteLogic.normalize(from).isEmpty();
        boolean hasTo = !ReverseRouteLogic.normalize(to).isEmpty();
        List<RouteStop> s = stops == null ? List.of() : stops;

        if (!hasFrom && !hasTo) return new Match(Kind.TOUCHES, -1, -1);

        if (hasFrom && hasTo) {
            if (matches(from, source) && matches(to, destination)) {
                return new Match(Kind.DIRECT, indexOfStop(s, from), indexOfStop(s, to));
            }
            int i = indexOfStop(s, from);
            int j = indexOfStop(s, to);
            int last = s.size() - 1;
            // The registered source / destination count as the two ends of the sheet.
            if (i < 0 && last >= 0) { if (matches(from, source)) i = 0; else if (matches(from, destination)) i = last; }
            if (j < 0 && last >= 0) { if (matches(to, destination)) j = last; else if (matches(to, source)) j = 0; }
            if (i >= 0 && j >= 0 && i != j) return new Match(i < j ? Kind.VIA : Kind.OPPOSITE, i, j);
            return null;
        }

        String q = hasFrom ? from : to;
        int idx = indexOfStop(s, q);
        if (idx >= 0 || matches(q, source) || matches(q, destination)) {
            return new Match(Kind.TOUCHES, hasFrom ? idx : -1, hasFrom ? -1 : idx);
        }
        return null;
    }

    static int rank(Kind k) {
        return switch (k) { case DIRECT -> 0; case VIA -> 1; case OPPOSITE -> 2; case TOUCHES -> 3; };
    }

    /** A message for the user if the edited stops can't be saved, otherwise null. */
    static String validateStops(List<RouteStop> stops) {
        if (stops == null || stops.size() < 2) return "A route needs at least 2 stops.";
        if (stops.size() > MAX_STOPS) return "Too many stops (max " + MAX_STOPS + ").";
        double prev = -1;
        for (int i = 0; i < stops.size(); i++) {
            RouteStop s = stops.get(i);
            String where = "Stop " + (i + 1) + (s.getStopName() == null || s.getStopName().isBlank() ? "" : " (" + s.getStopName() + ")");
            if (s.getStopName() == null || s.getStopName().isBlank()) return where + ": the name is empty.";
            if (s.getStopName().length() > 100) return where + ": the name is too long (max 100).";
            double lat = s.getLatitude(), lon = s.getLongitude();
            if (Double.isNaN(lat) || Double.isNaN(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180
                    || (lat == 0 && lon == 0)) {
                return where + ": latitude / longitude is not valid.";
            }
            double d = s.getDistanceFromStartKm();
            if (Double.isNaN(d) || Double.isInfinite(d) || d < 0) return where + ": the distance must be 0 or more.";
            if (i > 0 && d < prev) {
                return where + ": the distance (" + d + " km) is less than the previous stop's (" + prev + " km).";
            }
            prev = d;
            if (s.getSlackTimeMin() < 0 || s.getSlackTimeMin() > 180) return where + ": the halt must be between 0 and 180 minutes.";
        }
        return null;
    }

    /** Collapses odd whitespace (non-breaking spaces from copy-paste) and trims. */
    static String clean(String raw) {
        if (raw == null) return "";
        return raw.replace('\u00A0', ' ').replace('\u200B', ' ').replace('\uFEFF', ' ').trim().replaceAll("\\s+", " ");
    }

    static List<String> cleanBuses(List<String> raw) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (raw != null) for (String p : raw) {
            String b = clean(p).toUpperCase(Locale.ROOT);
            if (!b.isEmpty() && b.length() <= 40) out.add(b);
            if (out.size() >= MAX_LIST_ITEMS) break;
        }
        return new ArrayList<>(out);
    }

    static List<String> cleanTimes(List<String> raw) {
        java.util.TreeSet<String> out = new java.util.TreeSet<>();
        if (raw != null) for (String p : raw) {
            String t = normalizeTime(p);
            if (!t.isEmpty() && t.length() <= 20) out.add(t);
            if (out.size() >= MAX_LIST_ITEMS) break;
        }
        return new ArrayList<>(out);
    }

    /** "6:00", "06:00" and "6:00 AM" all become 06:00; anything unrecognised is kept as typed. */
    static String normalizeTime(String raw) {
        String text = clean(raw);
        if (text.isEmpty()) return text;
        for (String pattern : new String[]{"H:mm", "HH:mm", "h:mm a", "hh:mm a", "H.mm", "HH.mm", "HHmm"}) {
            try {
                return LocalTime.parse(text.toUpperCase(Locale.ROOT), DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
                        .format(DateTimeFormatter.ofPattern("HH:mm"));
            } catch (Exception ignored) { /* try the next pattern */ }
        }
        return text;
    }
}
