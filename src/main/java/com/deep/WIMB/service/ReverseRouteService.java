/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import com.deep.WIMB.dto.RouteStop;
import com.deep.WIMB.model.Route;
import com.deep.WIMB.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Creates the return route (B -> A) for a freshly uploaded A -> B route, for both the admin's and the
 * depot's "add route" flows. The return route gets its own Excel sheet with the stops reversed and the
 * distances recalculated, source and destination swapped, and the same bus roster (the same buses run
 * both ways). Departure times are NOT copied: the way back has its own timetable (Schedule tab).
 *
 * It never fails the upload that triggered it: if the return route can't be made, the caller gets an
 * Outcome saying why and the forward route stays exactly as uploaded.
 */
@Service
@RequiredArgsConstructor
public class ReverseRouteService {

    private final RouteRepository routeRepository;
    private final RouteExcelLoader routeExcelLoader;

    @Value("${wimb.route.dir}")
    private String routeDir;

    public record Outcome(boolean created, String routeCode, String routeName, int stopCount, String message) {}

    /**
     * @param forward       the route that was just saved and loaded (its stops must be in the loader cache)
     * @param requestedCode code for the return route, or blank to derive it (APD_FLK -> FLK_APD)
     * @param ownerDepot    the depot that owns the return route (null when the admin created it)
     */
    public Outcome createReturnRoute(Route forward, String requestedCode, String ownerDepot) {
        String src = forward.getSourceDepot();
        String dst = forward.getDestinationDepot();
        if (src == null || src.isBlank() || dst == null || dst.isBlank()) {
            return skipped("Return route not created: the source and destination depot of this route are not set.");
        }
        if (ReverseRouteLogic.normalize(src).equals(ReverseRouteLogic.normalize(dst))) {
            return skipped("Return route not created: source and destination are the same place.");
        }

        List<RouteStop> stops;
        try {
            stops = routeExcelLoader.getFullRoute(forward.getRouteCode());
        } catch (RuntimeException e) {
            return skipped("Return route not created: could not read the stops of " + forward.getRouteCode() + ".");
        }
        if (stops == null || stops.size() < 2) {
            return skipped("Return route not created: the route needs at least 2 stops.");
        }

        String code = requestedCode == null ? "" : requestedCode.trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty()) code = ReverseRouteLogic.reverseCode(forward.getRouteCode());
        if (code.equals(forward.getRouteCode())) code = code + "_RETURN";
        if (code.length() > 60) return skipped("Return route not created: the route code is too long (max 60).");
        if (routeRepository.existsByRouteCode(code)) {
            return skipped("Return route not created: a route with code \"" + code + "\" already exists.");
        }
        // Someone may already have registered the way back under another code: don't double it.
        for (Route r : routeRepository.findAll()) {
            if (ReverseRouteLogic.normalize(r.getSourceDepot()).equals(ReverseRouteLogic.normalize(dst))
                    && ReverseRouteLogic.normalize(r.getDestinationDepot()).equals(ReverseRouteLogic.normalize(src))) {
                return skipped("Return route not created: " + r.getRouteCode() + " already runs " + dst + " \u2192 " + src + ".");
            }
        }

        try {
            byte[] sheet = RouteSheetWriter.write(ReverseRouteLogic.reverseStops(stops));
            String name = ReverseRouteLogic.reverseName(forward.getRouteName(), src, dst);

            Route back = new Route();
            back.setRouteCode(code);
            back.setRouteName(name);
            back.setFilePath(routeDir + "/" + code + ".xlsx");
            back.setFileData(sheet);
            back.setUploadedAt(LocalDateTime.now());
            back.setSourceDepot(dst);
            back.setDestinationDepot(src);
            back.setOwnerDepot(ownerDepot);
            back.setBusNumbers(forward.getBusNumbers() == null ? new ArrayList<>() : new ArrayList<>(forward.getBusNumbers()));
            back.setDepartureTimes(new ArrayList<>());

            try {   // best-effort disk copy, same as the normal upload
                File dest = new File(routeDir, code + ".xlsx");
                dest.getParentFile().mkdirs();
                Files.write(dest.toPath(), sheet);
            } catch (Exception diskEx) {
                System.err.println("Note: could not also write route file to disk (non-fatal): " + diskEx.getMessage());
            }

            try {
                routeExcelLoader.loadRouteFromDisk(back);
                back.setStopCount(routeExcelLoader.getStopCount(code));
                routeRepository.save(back);
            } catch (Exception e) {
                routeExcelLoader.removeRoute(code);   // never leave a half-added route in the live cache
                throw e;
            }
            return new Outcome(true, code, name, back.getStopCount(), "Return route " + code + " added");
        } catch (Exception e) {
            return skipped("Return route could not be created: " + e.getMessage());
        }
    }

    /** JSON-friendly view of an Outcome for the controllers' responses. */
    public Map<String, Object> describe(Outcome o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("created", o.created());
        m.put("routeCode", o.routeCode());
        m.put("routeName", o.routeName());
        m.put("stopCount", o.stopCount());
        m.put("message", o.message());
        return m;
    }

    private static Outcome skipped(String why) { return new Outcome(false, null, null, 0, why); }
}
