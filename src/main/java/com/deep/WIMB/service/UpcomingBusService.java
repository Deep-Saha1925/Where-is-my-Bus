/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import com.deep.WIMB.dto.RouteStop;
import com.deep.WIMB.dto.UpcomingBus;
import com.deep.WIMB.model.Route;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "No bus is running right now" is not the end of the answer: if a passenger searches A to D
 * at 10:55 and a bus leaves at 11:00, this works out which scheduled buses are coming soon.
 *
 * It uses only data the admin already enters: each route's departure times (daily, from the
 * route's first stop in its running direction) and the route's stops with distances and
 * halts. There is no per-stop timetable, so the time at the passenger's stop is an estimate:
 *
 *     departure + (distance from the first stop / average speed) + halts of the stops before it
 *
 * The average speed (30 km/h by default) is the same one the home page uses for its ETA.
 * A bus is never tied to a departure time (the roster is just "buses on this route"), so
 * the result is a list of routes + times, not "BUS1 at 11:00".
 */
@Service
@RequiredArgsConstructor
public class UpcomingBusService {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern TIME = Pattern.compile("^(\\d{1,2}):(\\d{2})\\s*(AM|PM)?$", Pattern.CASE_INSENSITIVE);
    private static final int DUE_MINUTES = 5;
    private static final int MAX_LATE = 3;

    private final RouteExcelLoader loader;

    @Value("${wimb.schedule.zone:Asia/Kolkata}")
    private String zone;

    @Value("${wimb.schedule.avg-speed-kmh:30}")
    private double avgSpeedKmh;

    /** Upcoming buses leaving within this many minutes are listed (up to maxUpcoming of them). */
    @Value("${wimb.schedule.look-ahead-minutes:180}")
    private int lookAheadMinutes;

    /** A scheduled bus that left up to this many minutes ago and is not live is shown as LATE. */
    @Value("${wimb.schedule.late-grace-minutes:30}")
    private int lateGraceMinutes;

    @Value("${wimb.schedule.max-upcoming:5}")
    private int maxUpcoming;

    public List<UpcomingBus> suggest(String source, String destination, Set<String> liveRouteCodes) {
        ZoneId z;
        try {
            z = ZoneId.of(zone);
        } catch (DateTimeException e) {
            z = ZoneId.systemDefault();
        }
        return suggest(source, destination, liveRouteCodes, LocalDateTime.now(z));
    }

    /** Same as above with an explicit "now", so the logic can be tested. */
    public List<UpcomingBus> suggest(String source, String destination, Set<String> liveRouteCodes, LocalDateTime now) {
        LocalDateTime minute = now.truncatedTo(ChronoUnit.MINUTES);
        double speed = avgSpeedKmh > 0 ? avgSpeedKmh : 30;

        List<UpcomingBus> ahead = new ArrayList<>();
        List<UpcomingBus> late = new ArrayList<>();

        for (Route route : loader.getAllRoutes()) {
            List<String> times = route.getDepartureTimes();
            if (times == null || times.isEmpty()) continue;

            String code = route.getRouteCode();
            List<RouteStop> stops;
            try {
                stops = loader.getFullRoute(code);
            } catch (RuntimeException e) {
                continue; // route file failed to load: invisible to every search anyway
            }

            int a = indexOf(code, stops, source);
            int d = indexOf(code, stops, destination);
            if (a < 0 || d < 0 || a == d) continue;

            // The departure times belong to the route's own running direction (set by the admin
            // as source depot -> destination depot). The sheet's row order can be either way.
            int start = indexOf(code, stops, route.getSourceDepot());
            int end = indexOf(code, stops, route.getDestinationDepot());
            boolean forward = (start >= 0 && end >= 0) ? start < end : true;
            if (start < 0) start = forward ? 0 : stops.size() - 1;
            if (end < 0) end = forward ? stops.size() - 1 : 0;

            if ((a < d) != forward) continue;                 // searched the opposite way
            if (forward ? a < start : a > start) continue;    // bus starts after the passenger's stop
            if (forward ? d > end : d < end) continue;        // bus ends before the destination

            int step = forward ? 1 : -1;
            long offsetMin = Math.round(minutes(stops, start, a, step, speed));
            long rideMin = Math.round(minutes(stops, a, d, step, speed));
            double km = Math.abs(stops.get(d).getDistanceFromStartKm() - stops.get(a).getDistanceFromStartKm());
            int stopsBetween = Math.abs(d - a) + 1;
            boolean approximate = a != start;
            boolean live = liveRouteCodes != null && liveRouteCodes.contains(code);
            List<String> buses = route.getBusNumbers() == null ? List.of() : new ArrayList<>(route.getBusNumbers());

            for (String text : times) {
                LocalTime t = parseTime(text);
                if (t == null) continue;
                for (int dayShift = -1; dayShift <= 1; dayShift++) {
                    LocalDate day = minute.toLocalDate().plusDays(dayShift);
                    LocalDateTime departs = day.atTime(t);
                    LocalDateTime atSource = departs.plusMinutes(offsetMin);
                    long mins = Duration.between(minute, atSource).toMinutes();

                    String status = mins < 0 ? "LATE" : (mins <= DUE_MINUTES ? "DUE" : "UPCOMING");
                    if (mins < 0 && (live || mins < -lateGraceMinutes)) continue;

                    UpcomingBus bus = new UpcomingBus(
                            code, route.getRouteName(), status,
                            stops.get(start).getStopName(), departs.format(HHMM),
                            atSource.format(HHMM), atSource.plusMinutes(rideMin).format(HHMM),
                            mins, atSource.toLocalDate().isAfter(minute.toLocalDate()),
                            approximate, stopsBetween, Math.round(km * 10) / 10.0, buses);
                    if (mins >= 0) ahead.add(bus); else late.add(bus);
                }
            }
        }

        ahead.sort(Comparator.comparingLong(UpcomingBus::minutesUntil));
        List<UpcomingBus> result = new ArrayList<>();
        for (UpcomingBus b : ahead) {
            if (result.size() >= maxUpcoming || b.minutesUntil() > lookAheadMinutes) break;
            result.add(b);
        }
        // Nothing soon: still say when the next one is (it may be tomorrow's first bus).
        if (result.isEmpty() && !ahead.isEmpty()) result.add(ahead.get(0));

        late.sort(Comparator.comparingLong(UpcomingBus::minutesUntil).reversed()); // most recent first
        for (int i = 0; i < late.size() && i < MAX_LATE; i++) result.add(late.get(i));
        return result;
    }

    /** Minutes to ride from one stop index to another: distance / speed + halts of the stops between. */
    private static double minutes(List<RouteStop> stops, int from, int to, int step, double speed) {
        double km = Math.abs(stops.get(to).getDistanceFromStartKm() - stops.get(from).getDistanceFromStartKm());
        double halts = 0;
        for (int i = from + step; i != to && i >= 0 && i < stops.size(); i += step) {
            halts += Math.max(0, stops.get(i).getSlackTimeMin());
        }
        return km / speed * 60.0 + halts;
    }

    /** Index of a stop (by name) in the route's sheet, or -1. */
    private int indexOf(String routeCode, List<RouteStop> stops, String name) {
        if (name == null || name.isBlank()) return -1;
        try {
            int order = loader.getStopOrderByName(routeCode, name);
            for (int i = 0; i < stops.size(); i++) {
                if (stops.get(i).getStopOrder() == order) return i;
            }
        } catch (RuntimeException ignored) {
            // stop name not on this route
        }
        return -1;
    }

    /** "06:00", "6:00 AM", "6:05pm" to a LocalTime; null if it is not a time. */
    static LocalTime parseTime(String text) {
        if (text == null) return null;
        Matcher m = TIME.matcher(text.trim());
        if (!m.matches()) return null;
        int h = Integer.parseInt(m.group(1));
        int min = Integer.parseInt(m.group(2));
        String ap = m.group(3);
        if (ap != null) {
            ap = ap.toUpperCase(Locale.ROOT);
            if (h < 1 || h > 12) return null;
            if (ap.equals("PM") && h != 12) h += 12;
            if (ap.equals("AM") && h == 12) h = 0;
        }
        if (h > 23 || min > 59) return null;
        return LocalTime.of(h, min);
    }
}
