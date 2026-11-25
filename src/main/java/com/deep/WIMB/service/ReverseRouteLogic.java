/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import com.deep.WIMB.dto.RouteStop;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The pure rules for turning an A -> B route into its B -> A return route. No Spring, no database,
 * no Excel: just codes, names and stops, so every rule can be unit-tested on its own.
 */
final class ReverseRouteLogic {

    private ReverseRouteLogic() {}

    /** Separators between the two halves of a route name: "A_B", "A -> B", "A to B", "A ⇄ B", "A/B". */
    private static final Pattern NAME_SEPARATOR =
            Pattern.compile("(\\s*)(_|\\u2194|\\u21C4|\\u2192|->|\\bto\\b|/)(\\s*)", Pattern.CASE_INSENSITIVE);

    /**
     * APD_FLK -> FLK_APD, A_B -> B_A. A code that isn't exactly "X_Y" (three parts, or no underscore)
     * can't be swapped safely, so it gets a "_RETURN" suffix; the caller may override it.
     */
    static String reverseCode(String code) {
        String c = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        String[] parts = c.split("_", -1);
        if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
            return parts[1] + "_" + parts[0];
        }
        return c + "_RETURN";
    }

    /**
     * "Alipurduar -> Falakata" becomes "Falakata -> Alipurduar" (same separator, same spacing).
     * If the name has no recognisable separator, falls back to "DESTINATION -> SOURCE".
     */
    static String reverseName(String name, String source, String destination) {
        if (name != null) {
            Matcher m = NAME_SEPARATOR.matcher(name);
            if (m.find()) {
                String left = name.substring(0, m.start()).trim();
                String right = name.substring(m.end()).trim();
                if (!left.isEmpty() && !right.isEmpty()) {
                    return right + m.group(1) + m.group(2) + m.group(3) + left;
                }
            }
        }
        return (destination == null ? "" : destination.trim()) + " \u2192 " + (source == null ? "" : source.trim());
    }

    /**
     * The same road driven the other way:
     *   - stops in reverse order, stop_order renumbered 1..n
     *   - distance_km measured from the NEW first stop (old last stop = 0, old first stop = trip length)
     *   - latitude / longitude / name / halt (slack) stay with their stop
     * Returns deep copies; the input list is not touched.
     */
    static List<RouteStop> reverseStops(List<RouteStop> forward) {
        List<RouteStop> out = new ArrayList<>();
        if (forward == null || forward.isEmpty()) return out;
        int n = forward.size();
        double lastDistance = forward.get(n - 1).getDistanceFromStartKm();
        for (int i = 0; i < n; i++) {
            RouteStop s = forward.get(n - 1 - i);
            RouteStop c = new RouteStop();
            c.setStopOrder(i + 1);
            c.setStopName(s.getStopName());
            c.setLatitude(s.getLatitude());
            c.setLongitude(s.getLongitude());
            c.setDistanceFromStartKm(round3(Math.max(0.0, lastDistance - s.getDistanceFromStartKm())));
            c.setSlackTimeMin(s.getSlackTimeMin());
            out.add(c);
        }
        return out;
    }

    /** Letters and digits only, lower case: "Cooch Behar" and "COOCHBEHAR" compare equal. */
    static String normalize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) if (Character.isLetterOrDigit(ch)) sb.append(Character.toLowerCase(ch));
        return sb.toString();
    }

    private static double round3(double v) { return Math.round(v * 1000.0) / 1000.0; }
}
