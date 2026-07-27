package com.deep.WIMB.service;

import com.deep.WIMB.dto.RouteStop;
import com.deep.WIMB.model.Route;
import com.deep.WIMB.repository.RouteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RouteExcelLoader {

    // ── Legacy single-route cache — untouched, still backs today's
    //    passenger/driver flow via the no-arg methods below. ──
    private final Map<String, List<RouteStop>> routeCache = new HashMap<>();
    private final String routeKey = "DEMO_ROUTES";
    private static final String STOPS_JSON_PATH = "data/stops.json";

    // ── New multi-route cache — keyed by real routeCode, populated from
    //    admin-uploaded Excel files. Not yet used by RideController/
    //    RouteController; that wiring happens in a later stage. ──
    private final Map<String, List<RouteStop>> multiRouteCache = new ConcurrentHashMap<>();

    @Value("${wimb.route.stops.dir:data/stops}")
    private String stopsJsonDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RouteRepository routeRepository;

    public RouteExcelLoader(RouteRepository routeRepository) {
        this.routeRepository = routeRepository;
    }

    @PostConstruct
    public void loadRoute() throws Exception {
        // Legacy demo route — unchanged from before.
        loadRoute(routeKey, "routes/route_APD_FLK.xlsx");

        // New: load every admin-registered route into the multi-route cache.
        List<Route> registeredRoutes = routeRepository.findAll();
        if (registeredRoutes.isEmpty()) {
            System.out.println("No admin-uploaded routes yet — Routes tab is empty until one is added.");
        }
        for (Route route : registeredRoutes) {
            try {
                loadRouteFromDisk(route.getRouteCode(), route.getFilePath());
            } catch (Exception e) {
                System.err.println("Failed to load route " + route.getRouteCode() + ": " + e.getMessage());
            }
        }
    }

    // ================= LEGACY (unchanged) =================

    private void loadRoute(String routeKey, String path) throws Exception {

        InputStream is = new ClassPathResource(path).getInputStream();
        Workbook wb = WorkbookFactory.create(is);
        Sheet sheet = wb.getSheetAt(0);

        List<RouteStop> stops = new ArrayList<>();
        List<String> stopNames = new ArrayList<>();

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {

            Row r = sheet.getRow(i);
            if (r == null) continue;

            if (isAnyCellMissing(r, 0, 1, 2, 3, 4, 5)) {
                System.out.println("Skipping row " + i + " due to missing cells");
                continue;
            }

            String stopName = r.getCell(1)
                    .getStringCellValue()
                    .trim();

            RouteStop stop = new RouteStop();
            stop.setStopOrder((int) r.getCell(0).getNumericCellValue());
            stop.setStopName(stopName);
            stop.setLatitude(r.getCell(2).getNumericCellValue());
            stop.setLongitude(r.getCell(3).getNumericCellValue());
            stop.setDistanceFromStartKm(r.getCell(4).getNumericCellValue());
            stop.setSlackTimeMin((int) r.getCell(5).getNumericCellValue());

            stops.add(stop);
            stopNames.add(stopName);
        }

        routeCache.put(routeKey, stops);
        wb.close();

        updateStopsJson(stopNames);

        System.out.println("Loaded route " + routeKey + " with " + stops.size() + " stops");
    }

    private void updateStopsJson(List<String> stopNames) {
        try {
            Map<String, Object> json = new HashMap<>();
            json.put("stops", stopNames);

            File file = new File(STOPS_JSON_PATH);
            file.getParentFile().mkdirs();

            objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(file, json);

            System.out.println("Updated stops.json file");
        } catch (Exception e) {
            System.err.println("Error updating stops.json file");
            e.printStackTrace();
        }
    }

    private boolean isAnyCellMissing(Row row, int... idxs) {
        for (int idx : idxs) {
            Cell c = row.getCell(idx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (c == null) return true;
        }
        return false;
    }

    public int getStopOrderByName(String stopName) {
        return routeCache.values()
                .stream()
                .flatMap(List::stream)
                .filter(s -> s.getStopName().equalsIgnoreCase(stopName))
                .findFirst()
                .orElseThrow(() ->
                        new RuntimeException("Stop not found: " + stopName))
                .getStopOrder();
    }

    public List<RouteStop> getFullRoute() {
        List<RouteStop> route = routeCache.get(routeKey);
        if (route == null) {
            throw new RuntimeException("Route not loaded: " + routeKey);
        }
        return route;
    }

    public List<RouteStop> getRouteBetween(String source, String destination) {
        return sliceBetween(getFullRoute(), source, destination);
    }

    // ================= NEW: multi-route (admin-uploaded) =================

    /**
     * Loads one admin-uploaded route file (from disk, not classpath) into the
     * multi-route cache, and writes its own stops JSON. Safe to call again
     * for the same routeCode — this is what both startup and a fresh admin
     * upload call into.
     */
    public void loadRouteFromDisk(String routeCode, String filePath) throws Exception {
        try (InputStream is = new FileInputStream(filePath);
             Workbook wb = WorkbookFactory.create(is)) {

            Sheet sheet = wb.getSheetAt(0);
            List<RouteStop> stops = new ArrayList<>();
            List<String> stopNames = new ArrayList<>();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row r = sheet.getRow(i);
                if (r == null) continue;
                if (isAnyCellMissing(r, 0, 1, 2, 3, 4, 5)) {
                    System.out.println("Skipping row " + i + " in route " + routeCode + " — missing cells");
                    continue;
                }

                String stopName = r.getCell(1).getStringCellValue().trim();

                RouteStop stop = new RouteStop();
                stop.setStopOrder((int) r.getCell(0).getNumericCellValue());
                stop.setStopName(stopName);
                stop.setLatitude(r.getCell(2).getNumericCellValue());
                stop.setLongitude(r.getCell(3).getNumericCellValue());
                stop.setDistanceFromStartKm(r.getCell(4).getNumericCellValue());
                stop.setSlackTimeMin((int) r.getCell(5).getNumericCellValue());

                stops.add(stop);
                stopNames.add(stopName);
            }

            multiRouteCache.put(routeCode, stops);
            updateRouteStopsJson(routeCode, stopNames);

            System.out.println("Loaded route " + routeCode + " with " + stops.size() + " stops (multi-route)");
        }
    }

    private void updateRouteStopsJson(String routeCode, List<String> stopNames) {
        try {
            Map<String, Object> json = new HashMap<>();
            json.put("routeCode", routeCode);
            json.put("stops", stopNames);

            File file = new File(stopsJsonDir, routeCode + ".json");
            file.getParentFile().mkdirs();

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, json);
        } catch (Exception e) {
            System.err.println("Error updating stops JSON for route " + routeCode);
            e.printStackTrace();
        }
    }

    public List<RouteStop> getFullRoute(String routeCode) {
        List<RouteStop> route = multiRouteCache.get(routeCode);
        if (route == null) {
            throw new RuntimeException("Route not loaded: " + routeCode);
        }
        return route;
    }

    public List<RouteStop> getRouteBetween(String routeCode, String source, String destination) {
        return sliceBetween(getFullRoute(routeCode), source, destination);
    }

    public boolean isRouteLoaded(String routeCode) {
        return multiRouteCache.containsKey(routeCode);
    }

    public List<String> getLoadedRouteCodes() {
        return new ArrayList<>(multiRouteCache.keySet());
    }

    // ================= SHARED HELPER (same slicing logic either way) =================

    private List<RouteStop> sliceBetween(List<RouteStop> fullRoute, String source, String destination) {
        int sourceIdx = -1;
        int destIdx = -1;

        for (int i = 0; i < fullRoute.size(); i++) {
            String stopName = fullRoute.get(i).getStopName();
            if (stopName.equalsIgnoreCase(source)) sourceIdx = i;
            if (stopName.equalsIgnoreCase(destination)) destIdx = i;
        }

        if (sourceIdx == -1 || destIdx == -1) {
            throw new RuntimeException("Invalid source or destination");
        }

        List<RouteStop> segment;
        if (sourceIdx < destIdx) {
            segment = new ArrayList<>(fullRoute.subList(sourceIdx, destIdx + 1));
        } else {
            segment = new ArrayList<>(fullRoute.subList(destIdx, sourceIdx + 1));
            Collections.reverse(segment);
        }

        double baseDistance = segment.get(0).getDistanceFromStartKm();
        for (RouteStop stop : segment) {
            stop.setDistanceFromStartKm(Math.abs(stop.getDistanceFromStartKm() - baseDistance));
        }

        return segment;
    }
}