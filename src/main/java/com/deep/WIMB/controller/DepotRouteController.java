/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.controller;

import com.deep.WIMB.dto.RouteStop;
import com.deep.WIMB.enums.RideStatus;
import com.deep.WIMB.model.DriverReport;
import com.deep.WIMB.model.Route;
import com.deep.WIMB.repository.DriverReportRepository;
import com.deep.WIMB.repository.RideRepository;
import com.deep.WIMB.repository.RouteRepository;
import com.deep.WIMB.service.DepotService;
import com.deep.WIMB.service.ReverseRouteService;
import com.deep.WIMB.service.RouteAccess;
import com.deep.WIMB.service.RouteExcelLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * The depot's own "Routes" tab: the same add / edit / bus roster / timetable / stops-file
 * functions the admin has, but fenced to this depot.
 *
 * Who may do what (the depot comes from its login, never from the request):
 *   - see + edit  a route it OWNS (it created it) or that is registered to START at this depot
 *   - delete      only routes it owns (an admin-created route can only be deleted by the admin)
 *   - read only   routes that END at this depot
 *   - everything else is invisible and answers 404, so route codes of other depots can't be probed.
 *
 * A depot-created route goes live straight away (that is the point: less work for the admin), so
 * every uploaded sheet is checked BEFORE anything is replaced, and every add / file change / delete
 * leaves an already-approved entry in the admin's Reports tab as an audit trail.
 */
@RestController
@RequestMapping("/depot/api/routes")
@RequiredArgsConstructor
public class DepotRouteController {

    private static final List<String> ALLOWED_EXTENSIONS = List.of(".xlsx", ".xls", ".xlsm");
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9_]{3,40}$");
    private static final int MAX_OWNED_ROUTES = 50;
    private static final int MAX_STOPS = 300;
    private static final int MAX_LIST_ITEMS = 60;

    private final RouteRepository routeRepository;
    private final RouteExcelLoader routeExcelLoader;
    private final RideRepository rideRepository;
    private final DriverReportRepository reportRepository;
    private final AdminRouteController adminRouteController;   // only for the shared template / file download
    private final ReverseRouteService reverseRouteService;

    @Value("${wimb.route.dir}")
    private String routeDir;

    public record RouteView(String routeCode, String routeName, String sourceDepot, String destinationDepot,
                            int stopCount, List<String> busNumbers, List<String> departureTimes,
                            String uploadedAt, String ownerDepot, boolean canEdit, boolean canDelete) {}

    // ───────────────────────── list ─────────────────────────

    @GetMapping
    public List<RouteView> list(Authentication auth) {
        String depot = depotOf(auth);
        List<RouteView> out = new ArrayList<>();
        for (Route r : routeRepository.findAllByOrderByRouteNameAsc()) {
            boolean edit = canEdit(r, depot);
            boolean touchesHere = same(r.getDestinationDepot(), depot) || same(r.getSourceDepot(), depot);
            if (!edit && !touchesHere) continue;
            out.add(view(r, edit, canDelete(r, depot)));
        }
        return out;
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() throws Exception {
        return adminRouteController.downloadTemplate();
    }

    /** The Excel sheet currently stored for a route, so the depot can edit its stops and re-upload. */
    @GetMapping("/{routeCode}/file")
    public ResponseEntity<?> file(@PathVariable String routeCode, Authentication auth) {
        Route r = visible(routeCode, auth);
        if (r == null) return notFound();
        return adminRouteController.downloadRouteFile(r.getRouteCode());
    }

    // ───────────────────────── add ─────────────────────────

    @PostMapping
    public ResponseEntity<?> add(
            @RequestParam("routeName") String routeName,
            @RequestParam("destinationDepot") String destinationDepot,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "busNumbers", required = false) String busNumbers,
            @RequestParam(value = "departureTimes", required = false) String departureTimes,
            @RequestParam(value = "createReturn", required = false, defaultValue = "true") boolean createReturn,
            Authentication auth) {

        String depot = depotOf(auth);
        String name = clean(routeName);
        String dest = clean(destinationDepot);
        if (name.isEmpty() || name.length() > 100) return bad("Route name is required (max 100 characters)");
        if (dest.isEmpty() || dest.length() > 100) return bad("Destination is required (max 100 characters)");

        String ext = extensionOf(file);
        if (ext == null) return bad("Please upload an Excel file (.xlsx, .xls or .xlsm)");
        if (ownedCount(depot) >= MAX_OWNED_ROUTES) {
            return bad("Your depot has reached the limit of " + MAX_OWNED_ROUTES + " routes. Ask the admin to raise it.");
        }

        byte[] bytes;
        try { bytes = file.getBytes(); } catch (Exception e) { return bad("Could not read the uploaded file"); }
        String problem = checkSheet(bytes);
        if (problem != null) return bad(problem);

        String code = freeCode(depot, dest);
        Route route = new Route();
        route.setRouteCode(code);
        route.setRouteName(name);
        route.setFilePath(routeDir + "/" + code + ext);
        route.setFileData(bytes);
        route.setUploadedAt(LocalDateTime.now());
        route.setSourceDepot(depot);          // always this depot: a depot can't create routes for another one
        route.setDestinationDepot(dest);
        route.setOwnerDepot(depot);
        route.setBusNumbers(parseBuses(busNumbers));
        route.setDepartureTimes(parseTimes(departureTimes));
        try {
            routeExcelLoader.loadRouteFromDisk(route);
            route.setStopCount(routeExcelLoader.getStopCount(code));
            routeRepository.save(route);
        } catch (Exception e) {
            routeExcelLoader.removeRoute(code);     // never leave a half-added route in the live cache
            return ResponseEntity.status(500).body(Map.of("error", "Could not add the route: " + e.getMessage()));
        }
        audit(depot, DriverReport.ROUTE_REQUEST, code,
                "Depot added route " + code + " (" + name + ", " + route.getStopCount() + " stops).");

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("message", "Route added and live");
        res.put("routeCode", code);
        res.put("stopCount", route.getStopCount());

        // The way back (dest -> depot): its own reversed sheet, owned by this depot too. A problem here
        // never undoes the route that was just added; the depot is simply told why it wasn't created.
        if (createReturn) {
            if (ownedCount(depot) >= MAX_OWNED_ROUTES) {
                res.put("returnWarning", "Return route not created: your depot has reached the limit of "
                        + MAX_OWNED_ROUTES + " routes.");
            } else {
                ReverseRouteService.Outcome back =
                        reverseRouteService.createReturnRoute(route, freeCode(dest, depot), depot);
                res.put("returnRoute", reverseRouteService.describe(back));
                if (back.created()) {
                    audit(depot, DriverReport.ROUTE_REQUEST, back.routeCode(),
                            "Depot added return route " + back.routeCode() + " (" + back.routeName() + ", "
                                    + back.stopCount() + " stops), made from " + code + ".");
                } else {
                    res.put("returnWarning", back.message());
                }
            }
        }
        return ResponseEntity.ok(res);
    }

    // ───────────────────────── edit ─────────────────────────

    @PutMapping("/{routeCode}")
    public ResponseEntity<?> update(
            @PathVariable String routeCode,
            @RequestParam("routeName") String routeName,
            @RequestParam(value = "destinationDepot", required = false) String destinationDepot,
            @RequestParam(value = "busNumbers", required = false) String busNumbers,
            @RequestParam(value = "departureTimes", required = false) String departureTimes,
            @RequestParam(value = "file", required = false) MultipartFile file,
            Authentication auth) {

        String depot = depotOf(auth);
        Route route = visible(routeCode, auth);
        if (route == null) return notFound();
        if (!canEdit(route, depot)) return ResponseEntity.status(403).body(Map.of("error", "This route is read-only for your depot"));

        String name = clean(routeName);
        if (name.isEmpty() || name.length() > 100) return bad("Route name is required (max 100 characters)");
        String dest = destinationDepot == null ? null : clean(destinationDepot);
        if (dest != null && (dest.isEmpty() || dest.length() > 100)) return bad("Destination can't be empty (max 100 characters)");

        // Validate the new sheet first: nothing live changes unless it is good.
        byte[] newBytes = null;
        String ext = null;
        if (file != null && !file.isEmpty()) {
            ext = extensionOf(file);
            if (ext == null) return bad("Please upload an Excel file (.xlsx, .xls or .xlsm)");
            if (rideRepository.existsByRouteCodeAndStatus(route.getRouteCode(), RideStatus.ACTIVE)) {
                return ResponseEntity.status(409).body(Map.of("error",
                        "A bus is running on this route right now. Replace the stops file after the trip ends."));
            }
            try { newBytes = file.getBytes(); } catch (Exception e) { return bad("Could not read the uploaded file"); }
            String problem = checkSheet(newBytes);
            if (problem != null) return bad(problem);
        }

        // Keep what is needed to roll back if loading the new sheet fails.
        byte[] oldBytes = route.getFileData();
        route.setRouteName(name);
        if (dest != null) route.setDestinationDepot(dest);   // the source can't be changed: that would hand the route to someone else
        if (busNumbers != null) route.setBusNumbers(parseBuses(busNumbers));
        if (departureTimes != null) route.setDepartureTimes(parseTimes(departureTimes));

        if (newBytes != null) {
            try {
                route.setFileData(newBytes);
                routeExcelLoader.loadRouteFromDisk(route);
                route.setStopCount(routeExcelLoader.getStopCount(route.getRouteCode()));
            } catch (Exception e) {
                route.setFileData(oldBytes);
                try { routeExcelLoader.loadRouteFromDisk(route); } catch (Exception ignored) { /* keep serving what the cache has */ }
                return ResponseEntity.status(500).body(Map.of("error", "Could not use that file: " + e.getMessage()));
            }
            route.setUploadedAt(LocalDateTime.now());
        }
        routeRepository.save(route);
        if (newBytes != null) {
            audit(depot, DriverReport.ROUTE_CORRECTION, route.getRouteCode(),
                    "Depot replaced the stops file of route " + route.getRouteCode() + " (" + route.getStopCount() + " stops).");
        }
        return ResponseEntity.ok(Map.of("message", "Route updated", "stopCount", route.getStopCount()));
    }

    // ───────────────────────── delete ─────────────────────────

    @DeleteMapping("/{routeCode}")
    public ResponseEntity<?> delete(@PathVariable String routeCode, Authentication auth) {
        String depot = depotOf(auth);
        Route route = visible(routeCode, auth);
        if (route == null) return notFound();
        if (!canDelete(route, depot)) {
            return ResponseEntity.status(403).body(Map.of("error",
                    "Only the admin can delete this route. Send the admin a request instead."));
        }
        if (rideRepository.existsByRouteCodeAndStatus(route.getRouteCode(), RideStatus.ACTIVE)) {
            return ResponseEntity.status(409).body(Map.of("error",
                    "A bus is running on this route right now. Delete it after the trip ends."));
        }
        String code = route.getRouteCode();
        routeExcelLoader.removeRoute(code);
        routeRepository.delete(route);
        audit(depot, DriverReport.OTHER, code, "Depot deleted its route " + code + " (" + route.getRouteName() + ").");
        return ResponseEntity.ok(Map.of("message", "Route deleted"));
    }

    // ───────────────────────── rules + helpers ─────────────────────────

    private static String depotOf(Authentication auth) {
        return auth.getName().substring(DepotService.LOGIN_PREFIX.length());
    }

    private static String norm(String s) { return RouteAccess.norm(s); }
    private static boolean same(String a, String b) { return RouteAccess.same(a, b); }

    /** Owner, or an admin-created route that is registered to START at this depot. */
    static boolean canEdit(Route r, String depot) { return RouteAccess.canEdit(r, depot); }
    static boolean canDelete(Route r, String depot) { return RouteAccess.canDelete(r, depot); }

    /** The route if this depot may at least see it, else null (so unknown and foreign codes look the same). */
    private Route visible(String routeCode, Authentication auth) {
        String depot = depotOf(auth);
        Route r = routeRepository.findByRouteCode(routeCode == null ? "" : routeCode.trim().toUpperCase(Locale.ROOT)).orElse(null);
        if (r == null) return null;
        return (canEdit(r, depot) || same(r.getDestinationDepot(), depot) || same(r.getSourceDepot(), depot)) ? r : null;
    }

    private RouteView view(Route r, boolean edit, boolean del) {
        return new RouteView(r.getRouteCode(), r.getRouteName(), r.getSourceDepot(), r.getDestinationDepot(),
                r.getStopCount(),
                r.getBusNumbers() == null ? List.of() : new ArrayList<>(r.getBusNumbers()),
                r.getDepartureTimes() == null ? List.of() : new ArrayList<>(r.getDepartureTimes()),
                DriverReport.toInstant(r.getUploadedAt()), r.getOwnerDepot(), edit, del);
    }

    private static ResponseEntity<?> bad(String msg) { return ResponseEntity.badRequest().body(Map.of("error", msg)); }
    private static ResponseEntity<?> notFound() { return ResponseEntity.status(404).body(Map.of("error", "Route not found")); }

    /** Collapses odd whitespace (non-breaking spaces from copy-paste) and trims. */
    private static String clean(String raw) {
        if (raw == null) return "";
        return raw.replace('\u00A0', ' ').replace('\u200B', ' ').replace('\uFEFF', ' ').trim().replaceAll("\\s+", " ");
    }

    private static String extensionOf(MultipartFile file) {
        String fn = file == null ? null : file.getOriginalFilename();
        if (file == null || file.isEmpty() || fn == null) return null;
        String low = fn.toLowerCase(Locale.ROOT);
        return ALLOWED_EXTENSIONS.stream().filter(low::endsWith).findFirst().orElse(null);
    }

    /** Null when the sheet is usable, otherwise a message for the depot. */
    private String checkSheet(byte[] bytes) {
        List<RouteStop> stops;
        try {
            stops = routeExcelLoader.parseBytes(bytes);
        } catch (Exception e) {
            return "Could not read that Excel file. Please use the template. (" + e.getMessage() + ")";
        }
        if (stops == null || stops.size() < 2) return "The sheet needs at least 2 stops. Please use the template.";
        if (stops.size() > MAX_STOPS) return "The sheet has too many stops (max " + MAX_STOPS + ").";
        int row = 0;
        for (RouteStop s : stops) {
            row++;
            if (s.getStopName() == null || s.getStopName().isBlank()) return "Stop " + row + " has no name.";
            double lat = s.getLatitude(), lon = s.getLongitude();
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180 || (lat == 0 && lon == 0)) {
                return "Stop \"" + s.getStopName() + "\" has an invalid latitude/longitude.";
            }
        }
        return null;
    }

    private long ownedCount(String depot) {
        return routeRepository.findAllByOrderByRouteNameAsc().stream()
                .filter(r -> depot.equalsIgnoreCase(r.getOwnerDepot())).count();
    }

    /** A route code nobody has used: FIRST_SECOND, then _2, _3 ... (depot first for the way there, destination first for the way back). */
    private String freeCode(String depot, String dest) {
        String base = (slug(depot) + "_" + slug(dest));
        if (base.length() > 34) base = base.substring(0, 34);
        if (!CODE_PATTERN.matcher(base).matches()) base = "ROUTE_" + System.currentTimeMillis() % 100000;
        String code = base;
        for (int i = 2; routeRepository.existsByRouteCode(code); i++) code = base + "_" + i;
        return code;
    }
    private static String slug(String s) {
        String t = norm(s).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        return t.isEmpty() ? "X" : t;
    }

    private List<String> parseBuses(String raw) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (raw != null) for (String p : raw.split(",")) {
            String b = clean(p).toUpperCase(Locale.ROOT);
            if (!b.isEmpty() && b.length() <= 40) out.add(b);
            if (out.size() >= MAX_LIST_ITEMS) break;
        }
        return new ArrayList<>(out);
    }

    private List<String> parseTimes(String raw) {
        TreeSet<String> out = new TreeSet<>();
        if (raw != null) for (String p : raw.split(",")) {
            String t = normalizeTime(p);
            if (!t.isEmpty() && t.length() <= 20) out.add(t);
            if (out.size() >= MAX_LIST_ITEMS) break;
        }
        return new ArrayList<>(out);
    }

    /** "6:00", "06:00" and "6:00 AM" all become 06:00; anything unrecognised is kept as typed. */
    private static String normalizeTime(String raw) {
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

    /** An already-approved entry in the admin's Reports tab: who changed what. */
    private void audit(String depot, String type, String routeCode, String text) {
        try {
            LocalDateTime now = LocalDateTime.now();
            DriverReport r = new DriverReport();
            r.setSource(DriverReport.SOURCE_DEPOT);
            r.setDepotName(depot);
            r.setBusNumber(DriverReport.NO_BUS);
            r.setType(type);
            r.setPlace(routeCode);
            r.setDescription(text);
            r.setStatus(DriverReport.APPROVED);
            r.setCreatedAt(now);
            r.setUpdatedAt(now);
            reportRepository.save(r);
        } catch (Exception e) {
            System.err.println("Note: could not write route audit entry (non-fatal): " + e.getMessage());
        }
    }
}
