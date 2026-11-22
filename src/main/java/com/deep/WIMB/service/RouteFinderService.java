/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import com.deep.WIMB.dto.RouteStop;
import com.deep.WIMB.enums.RideStatus;
import com.deep.WIMB.model.DriverReport;
import com.deep.WIMB.model.Route;
import com.deep.WIMB.repository.DriverReportRepository;
import com.deep.WIMB.repository.RideRepository;
import com.deep.WIMB.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Route Finder: search routes "from A to B", look at their stops and update them. Used by the admin
 * (everything is editable) and by depots (everything is visible, only their own routes are editable,
 * same rule as the depot's Routes tab: {@link RouteAccess}).
 *
 * Errors are thrown as ResponseStatusException; ApiExceptionHandler turns them into {"error": "..."}.
 */
@Service
@RequiredArgsConstructor
public class RouteFinderService {

    private static final int MAX_RESULTS = 100;

    private final RouteRepository routeRepository;
    private final RouteExcelLoader routeExcelLoader;
    private final RideRepository rideRepository;
    private final DriverReportRepository reportRepository;

    /** Who is asking. depot is null for the admin. */
    public record Actor(boolean admin, String depot) {
        public static Actor asAdmin() { return new Actor(true, null); }
        public static Actor asDepot(String depot) { return new Actor(false, depot); }
    }

    public record StopInput(String stopName, Double latitude, Double longitude,
                            Double distanceFromStartKm, Double slackTimeMin) {}

    public record DetailsInput(String routeName, String sourceDepot, String destinationDepot,
                               List<String> busNumbers, List<String> departureTimes) {}

    // ───────────────────────── search ─────────────────────────

    public List<Map<String, Object>> search(String from, String to, Actor actor) {
        record Scored(int rank, String name, Map<String, Object> hit) {}
        List<Scored> found = new ArrayList<>();

        for (Route r : routeRepository.findAllByOrderByRouteNameAsc()) {
            List<RouteStop> stops = stopsOf(r);
            RouteFinderLogic.Match m = RouteFinderLogic.match(from, to, r.getSourceDepot(), r.getDestinationDepot(), stops);
            if (m == null) continue;

            Map<String, Object> hit = new LinkedHashMap<>();
            hit.put("routeCode", r.getRouteCode());
            hit.put("routeName", r.getRouteName());
            hit.put("sourceDepot", r.getSourceDepot());
            hit.put("destinationDepot", r.getDestinationDepot());
            hit.put("stopCount", r.getStopCount());
            hit.put("busNumbers", r.getBusNumbers() == null ? List.of() : new ArrayList<>(r.getBusNumbers()));
            hit.put("departureTimes", r.getDepartureTimes() == null ? List.of() : new ArrayList<>(r.getDepartureTimes()));
            hit.put("ownerDepot", r.getOwnerDepot());
            hit.put("canEdit", canEdit(r, actor));
            hit.put("match", m.kind().name());
            hit.put("fromStop", m.fromIndex() >= 0 && m.fromIndex() < stops.size() ? stops.get(m.fromIndex()).getStopName() : null);
            hit.put("toStop", m.toIndex() >= 0 && m.toIndex() < stops.size() ? stops.get(m.toIndex()).getStopName() : null);
            found.add(new Scored(RouteFinderLogic.rank(m.kind()), r.getRouteName() == null ? "" : r.getRouteName(), hit));
        }
        found.sort(Comparator.comparingInt(Scored::rank).thenComparing(Scored::name, String.CASE_INSENSITIVE_ORDER));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Scored s : found) {
            out.add(s.hit());
            if (out.size() >= MAX_RESULTS) break;
        }
        return out;
    }

    // ───────────────────────── one route ─────────────────────────

    public Map<String, Object> detail(String routeCode, Actor actor) {
        Route r = find(routeCode);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("routeCode", r.getRouteCode());
        m.put("routeName", r.getRouteName());
        m.put("sourceDepot", r.getSourceDepot());
        m.put("destinationDepot", r.getDestinationDepot());
        m.put("ownerDepot", r.getOwnerDepot());
        m.put("busNumbers", r.getBusNumbers() == null ? List.of() : new ArrayList<>(r.getBusNumbers()));
        m.put("departureTimes", r.getDepartureTimes() == null ? List.of() : new ArrayList<>(r.getDepartureTimes()));
        m.put("stopCount", r.getStopCount());
        m.put("canEdit", canEdit(r, actor));
        m.put("running", isRunning(r));

        List<Map<String, Object>> stops = new ArrayList<>();
        for (RouteStop s : stopsOf(r)) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("stopName", s.getStopName());
            o.put("latitude", s.getLatitude());
            o.put("longitude", s.getLongitude());
            o.put("distanceFromStartKm", s.getDistanceFromStartKm());
            o.put("slackTimeMin", s.getSlackTimeMin());
            stops.add(o);
        }
        m.put("stops", stops);

        Route back = oppositeOf(r);
        if (back != null) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("routeCode", back.getRouteCode());
            o.put("routeName", back.getRouteName());
            o.put("canEdit", canEdit(back, actor));
            m.put("opposite", o);
        }
        return m;
    }

    // ───────────────────────── update stops ─────────────────────────

    public Map<String, Object> saveStops(String routeCode, List<StopInput> input, boolean alsoReturn, Actor actor) {
        Route r = find(routeCode);
        requireEdit(r, actor);

        List<RouteStop> stops = toStops(input);
        String problem = RouteFinderLogic.validateStops(stops);
        if (problem != null) throw bad(problem);
        if (isRunning(r)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A bus is running on this route right now. Change its stops after the trip ends.");
        }

        applyStops(r, stops);
        if (!actor.admin()) {
            audit(actor.depot(), r.getRouteCode(),
                    "Depot changed the stops of route " + r.getRouteCode() + " (" + r.getStopCount() + " stops now).");
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("message", "Stops saved");
        res.put("stopCount", r.getStopCount());

        if (alsoReturn) {
            Map<String, Object> back = new LinkedHashMap<>();
            Route o = oppositeOf(r);
            if (o == null) {
                back.put("updated", false);
                back.put("note", "There is no return route to update.");
            } else if (!canEdit(o, actor)) {
                back.put("updated", false);
                back.put("routeCode", o.getRouteCode());
                back.put("note", "Return route " + o.getRouteCode() + " was not changed: you can't edit it.");
            } else if (isRunning(o)) {
                back.put("updated", false);
                back.put("routeCode", o.getRouteCode());
                back.put("note", "Return route " + o.getRouteCode() + " was not changed: a bus is running on it right now.");
            } else {
                try {
                    applyStops(o, ReverseRouteLogic.reverseStops(stops));
                    back.put("updated", true);
                    back.put("routeCode", o.getRouteCode());
                    back.put("stopCount", o.getStopCount());
                    back.put("note", "Return route " + o.getRouteCode() + " updated too.");
                    if (!actor.admin()) {
                        audit(actor.depot(), o.getRouteCode(),
                                "Depot changed the stops of return route " + o.getRouteCode() + " (made from " + r.getRouteCode() + ").");
                    }
                } catch (ResponseStatusException e) {
                    back.put("updated", false);
                    back.put("routeCode", o.getRouteCode());
                    back.put("note", "Return route " + o.getRouteCode() + " was not changed: " + e.getReason());
                }
            }
            res.put("returnRoute", back);
        }
        return res;
    }

    // ───────────────────────── update details ─────────────────────────

    public Map<String, Object> saveDetails(String routeCode, DetailsInput in, Actor actor) {
        Route r = find(routeCode);
        requireEdit(r, actor);
        if (in == null) throw bad("Nothing to save.");

        String name = RouteFinderLogic.clean(in.routeName());
        if (name.isEmpty() || name.length() > 100) throw bad("Route name is required (max 100 characters).");
        String dest = RouteFinderLogic.clean(in.destinationDepot());
        if (dest.length() > 100) throw bad("Destination is too long (max 100 characters).");
        if (!actor.admin() && dest.isEmpty()) throw bad("Destination can't be empty.");

        r.setRouteName(name);
        r.setDestinationDepot(dest.isEmpty() ? null : dest);
        if (actor.admin()) {   // only the admin may change where a route starts
            String src = RouteFinderLogic.clean(in.sourceDepot());
            if (src.length() > 100) throw bad("Source is too long (max 100 characters).");
            r.setSourceDepot(src.isEmpty() ? null : src);
        }
        r.setBusNumbers(RouteFinderLogic.cleanBuses(in.busNumbers()));
        r.setDepartureTimes(RouteFinderLogic.cleanTimes(in.departureTimes()));
        routeRepository.save(r);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("message", "Details saved");
        return res;
    }

    // ───────────────────────── helpers ─────────────────────────

    private Route find(String routeCode) {
        String code = routeCode == null ? "" : routeCode.trim().toUpperCase(Locale.ROOT);
        return routeRepository.findByRouteCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found"));
    }

    private static boolean canEdit(Route r, Actor a) {
        return a.admin() || RouteAccess.canEdit(r, a.depot());
    }

    private static void requireEdit(Route r, Actor a) {
        if (!canEdit(r, a)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only change routes that start at your depot or that your depot added. Send the admin a request instead.");
        }
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    private List<RouteStop> stopsOf(Route r) {
        try {
            return routeExcelLoader.getFullRoute(r.getRouteCode());
        } catch (RuntimeException e) {
            return List.of();    // not loaded (broken sheet): still searchable by its registered source / destination
        }
    }

    private boolean isRunning(Route r) {
        return rideRepository.existsByRouteCodeAndStatus(r.getRouteCode(), RideStatus.ACTIVE);
    }

    /** The route that runs the same road the other way (source and destination swapped), if there is one. */
    private Route oppositeOf(Route r) {
        String src = ReverseRouteLogic.normalize(r.getSourceDepot());
        String dst = ReverseRouteLogic.normalize(r.getDestinationDepot());
        if (src.isEmpty() || dst.isEmpty()) return null;
        for (Route o : routeRepository.findAll()) {
            if (o.getRouteCode().equals(r.getRouteCode())) continue;
            if (ReverseRouteLogic.normalize(o.getSourceDepot()).equals(dst)
                    && ReverseRouteLogic.normalize(o.getDestinationDepot()).equals(src)) return o;
        }
        return null;
    }

    private List<RouteStop> toStops(List<StopInput> input) {
        if (input == null) throw bad("No stops were sent.");
        if (input.size() > RouteFinderLogic.MAX_STOPS) throw bad("Too many stops (max " + RouteFinderLogic.MAX_STOPS + ").");
        List<RouteStop> out = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            StopInput in = input.get(i);
            if (in == null || in.latitude() == null || in.longitude() == null
                    || in.distanceFromStartKm() == null || in.slackTimeMin() == null) {
                throw bad("Stop " + (i + 1) + ": please fill in every field.");
            }
            RouteStop s = new RouteStop();
            s.setStopOrder(i + 1);
            s.setStopName(RouteFinderLogic.clean(in.stopName()));
            s.setLatitude(in.latitude());
            s.setLongitude(in.longitude());
            s.setDistanceFromStartKm(Math.round(in.distanceFromStartKm() * 1000.0) / 1000.0);
            s.setSlackTimeMin((int) Math.round(in.slackTimeMin()));
            out.add(s);
        }
        return out;
    }

    /** Writes the new sheet, reloads the live route, saves. If the new sheet won't load, the old one is put back. */
    private void applyStops(Route r, List<RouteStop> stops) {
        byte[] oldBytes = r.getFileData();
        String oldPath = r.getFilePath();
        try {
            r.setFileData(RouteSheetWriter.write(stops));
            routeExcelLoader.loadRouteFromDisk(r);
            r.setStopCount(routeExcelLoader.getStopCount(r.getRouteCode()));
        } catch (Exception e) {
            r.setFileData(oldBytes);
            r.setFilePath(oldPath);
            try { routeExcelLoader.loadRouteFromDisk(r); } catch (Exception ignored) { /* keep serving what the cache has */ }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not apply the new stops: " + e.getMessage());
        }
        r.setUploadedAt(LocalDateTime.now());
        routeRepository.save(r);
    }

    /** An already-approved entry in the admin's Reports tab: who changed what. */
    private void audit(String depot, String routeCode, String text) {
        try {
            LocalDateTime now = LocalDateTime.now();
            DriverReport rep = new DriverReport();
            rep.setSource(DriverReport.SOURCE_DEPOT);
            rep.setDepotName(depot);
            rep.setBusNumber(DriverReport.NO_BUS);
            rep.setType(DriverReport.ROUTE_CORRECTION);
            rep.setPlace(routeCode);
            rep.setDescription(text);
            rep.setStatus(DriverReport.APPROVED);
            rep.setCreatedAt(now);
            rep.setUpdatedAt(now);
            reportRepository.save(rep);
        } catch (Exception e) {
            System.err.println("Note: could not write route audit entry (non-fatal): " + e.getMessage());
        }
    }
}
