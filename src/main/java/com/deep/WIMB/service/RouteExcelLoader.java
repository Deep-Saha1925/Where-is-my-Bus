package com.deep.WIMB.service;

import com.deep.WIMB.dto.RouteStop;
import com.deep.WIMB.model.Route;
import com.deep.WIMB.repository.RouteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RouteExcelLoader {

    private final RouteRepository routeRepository;

    // Keyed by route code. "DEMO_ROUTES" is the original legacy single route —
    // still used by today's passenger/driver flow until that's migrated (stage 3).
    // Anything the admin uploads lives in this same map under its real route code.
    private final Map<String, List<RouteStop>> routeCache = new HashMap<>();

    private final String legacyRouteKey = "DEMO_ROUTES";
    private static final String STOPS_JSON_PATH = "data/stops.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() throws Exception {
        loadLegacyRoute();          // unchanged behavior
        loadAllRegisteredRoutes();  // new: load whatever admin has already added
    }

    /* ============== LEGACY (unchanged behavior) ============== */

    private void loadLegacyRoute() throws Exception {
        InputStream is = new ClassPathResource("routes/route_APD_FLK.xlsx").getInputStream();
        try (Workbook wb = WorkbookFactory.create(is)) {
            List<RouteStop> stops = parseWorkbook(wb);
            routeCache.put(legacyRouteKey, stops);
            updateStopsJson(stops);
            System.out.println("Loaded legacy route " + legacyRouteKey + " with " + stops.size() + " stops");
        }
    }

    public int getStopOrderByName(String stopName) {
        return getStopOrderByName(legacyRouteKey, stopName);
    }

    public List<RouteStop> getFullRoute() {
        return getFullRoute(legacyRouteKey);
    }

    public List<RouteStop> getRouteBetween(String source, String destination) {
        return sliceBetween(getFullRoute(legacyRouteKey), source, destination);
    }

    /* ============== NEW: MULTI-ROUTE (admin-managed) ============== */

    /** Loads every registered Route from the DB into the cache. Safe to call again later. */
    public void loadAllRegisteredRoutes() {
        for (Route route : routeRepository.findAll()) {
            try {
                loadRouteFromDisk(route);
            } catch (Exception e) {
                System.err.println("Failed to load route " + route.getRouteCode() + ": " + e.getMessage());
            }
        }
    }

    /** (Re)loads a single route's Excel file from disk into the cache. */
    public void loadRouteFromDisk(Route route) throws Exception {
        File file = new File(route.getFilePath());
        if (!file.exists()) {
            throw new RuntimeException("Route file missing on disk: " + route.getFilePath());
        }
        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = WorkbookFactory.create(fis)) {
            List<RouteStop> stops = parseWorkbook(wb);
            routeCache.put(route.getRouteCode(), stops);
            System.out.println("Loaded route " + route.getRouteCode()
                    + " (" + route.getRouteName() + ") with " + stops.size() + " stops");
        }
    }

    public void reloadRoute(String routeCode) {
        Route route = routeRepository.findByRouteCode(routeCode)
                .orElseThrow(() -> new RuntimeException("Route not found: " + routeCode));
        try {
            loadRouteFromDisk(route);
        } catch (Exception e) {
            throw new RuntimeException("Failed to reload route " + routeCode + ": " + e.getMessage(), e);
        }
    }

    public int getStopCount(String routeCode) {
        List<RouteStop> stops = routeCache.get(routeCode);
        return stops == null ? 0 : stops.size();
    }

    /** For the admin table (and later, the driver's route picker). */
    public List<Route> getAllRoutes() {
        return routeRepository.findAllByOrderByRouteNameAsc();
    }

    public List<RouteStop> getFullRoute(String routeCode) {
        List<RouteStop> route = routeCache.get(routeCode);
        if (route == null) {
            throw new RuntimeException("Route not loaded: " + routeCode);
        }
        return route;
    }

    public int getStopOrderByName(String routeCode, String stopName) {
        return getFullRoute(routeCode).stream()
                .filter(s -> s.getStopName().equalsIgnoreCase(stopName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Stop not found: " + stopName))
                .getStopOrder();
    }

    public List<RouteStop> getRouteBetween(String routeCode, String source, String destination) {
        return sliceBetween(getFullRoute(routeCode), source, destination);
    }

    /* ============== SHARED HELPERS ============== */

    private List<RouteStop> parseWorkbook(Workbook wb) {
        Sheet sheet = wb.getSheetAt(0);
        List<RouteStop> stops = new ArrayList<>();

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row r = sheet.getRow(i);
            if (r == null) continue;

            if (isAnyCellMissing(r, 0, 1, 2, 3, 4, 5)) {
                System.out.println("Skipping row " + i + " due to missing cells");
                continue;
            }

            RouteStop stop = new RouteStop();
            stop.setStopOrder((int) r.getCell(0).getNumericCellValue());
            stop.setStopName(r.getCell(1).getStringCellValue().trim());
            stop.setLatitude(r.getCell(2).getNumericCellValue());
            stop.setLongitude(r.getCell(3).getNumericCellValue());
            stop.setDistanceFromStartKm(r.getCell(4).getNumericCellValue());
            stop.setSlackTimeMin((int) r.getCell(5).getNumericCellValue());
            stops.add(stop);
        }
        return stops;
    }

    private List<RouteStop> sliceBetween(List<RouteStop> fullRoute, String source, String destination) {
        int sourceIdx = -1, destIdx = -1;
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

    private void updateStopsJson(List<RouteStop> stops) {
        try {
            List<String> stopNames = stops.stream().map(RouteStop::getStopName).toList();
            Map<String, Object> json = new HashMap<>();
            json.put("stops", stopNames);

            File file = new File(STOPS_JSON_PATH);
            file.getParentFile().mkdirs();

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, json);
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
}