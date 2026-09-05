package com.deep.WIMB.service;

import com.deep.WIMB.dto.DepotRouteMatch;
import com.deep.WIMB.dto.RouteStop;
import com.deep.WIMB.model.Route;
import com.deep.WIMB.repository.RouteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
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
        loadLegacyRoute();
        loadAllRegisteredRoutes();
        updateStopsJson();
    }

    private void loadLegacyRoute() throws Exception {
        InputStream is = new ClassPathResource("routes/route_APD_FLK.xlsx").getInputStream();
        try (Workbook wb = WorkbookFactory.create(is)) {
            List<RouteStop> stops = parseWorkbook(wb);
            routeCache.put(legacyRouteKey, stops);
            System.out.println("Loaded legacy route " + legacyRouteKey + " with " + stops.size() + " stops");
        }
    }

    public String getLegacyRouteKey() {
        return legacyRouteKey;
    }

    /** Resolves a possibly-null/blank routeCode down to the legacy route key. */
    public String resolveRouteCode(String routeCode) {
        return (routeCode == null || routeCode.isBlank()) ? legacyRouteKey : routeCode;
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

    /** (Re)loads a single route into the cache — from its stored DB bytes if
     *  present, otherwise (for routes saved before this migration) from disk
     *  as a one-time fallback that immediately persists the bytes into the
     *  DB so every future load no longer depends on disk at all. */
    public void loadRouteFromDisk(Route route) throws Exception {
        byte[] bytes = route.getFileData();

        if (bytes == null) {
            // Legacy route saved before file content moved into Postgres —
            // recover it from disk this one time, if it's still there.
            // Paths may have been saved on a different OS (Windows
            // backslashes) than this one (Linux in production); a literal
            // backslash isn't a separator on Linux, so normalize first.
            String normalizedPath = route.getFilePath().replace('\\', '/');
            File file = new File(normalizedPath);
            if (!file.exists()) {
                throw new RuntimeException(
                        "Route file missing on disk and no data stored in the database: " + normalizedPath
                                + " — the file needs to be re-uploaded via the admin panel.");
            }
            bytes = Files.readAllBytes(file.toPath());

            // Migrate: persist into the DB right now so this fallback path
            // is never needed again for this route, even after the disk
            // file is inevitably lost on the next container restart.
            route.setFileData(bytes);
            routeRepository.save(route);
            System.out.println("Migrated route " + route.getRouteCode()
                    + " from disk into the database — it no longer depends on local disk.");
        }

        try (InputStream is = new ByteArrayInputStream(bytes);
             Workbook wb = WorkbookFactory.create(is)) {
            List<RouteStop> stops = parseWorkbook(wb);
            routeCache.put(route.getRouteCode(), stops);
            System.out.println("Loaded route " + route.getRouteCode()
                    + " (" + route.getRouteName() + ") with " + stops.size() + " stops");
        }
        // Every admin-added/updated route needs to surface its stops on the
        // passenger search page too, not just the legacy route — otherwise
        // a newly-added route's stop names never appear in the datalist.
        updateStopsJson();
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

    /**
     * Static, non-live search: which registered route(s) connect two depot
     * names, regardless of whether any bus is currently on the road. This
     * checks every loaded route (legacy + admin-registered) for both stop
     * names, so a depot can be an endpoint OR an intermediate stop.
     */
    public List<DepotRouteMatch> findRoutesBetweenDepots(String source, String destination) {
        List<DepotRouteMatch> matches = new ArrayList<>();

        for (Map.Entry<String, List<RouteStop>> entry : routeCache.entrySet()) {
            String routeCode = entry.getKey();
            List<RouteStop> fullRoute = entry.getValue();

            List<RouteStop> segment;
            try {
                segment = sliceBetween(fullRoute, source, destination);
            } catch (RuntimeException notFound) {
                continue; // this route doesn't touch both depots — skip it
            }

            String routeName;
            List<String> busNumbers;
            List<String> departureTimes;
            if (routeCode.equals(legacyRouteKey)) {
                routeName = "Alipurduar \u21C4 Falakata (default route)";
                busNumbers = Collections.emptyList();
                departureTimes = Collections.emptyList();
            } else {
                Route route = routeRepository.findByRouteCode(routeCode).orElse(null);
                if (route == null) continue; // shouldn't happen, but stay safe
                routeName = route.getRouteName();
                busNumbers = route.getBusNumbers() == null ? Collections.emptyList() : route.getBusNumbers();
                departureTimes = route.getDepartureTimes() == null ? Collections.emptyList() : route.getDepartureTimes();
            }

            double distance = segment.isEmpty() ? 0 : segment.get(segment.size() - 1).getDistanceFromStartKm();

            matches.add(new DepotRouteMatch(
                    routeCode, routeName, source, destination,
                    segment.size(), distance, busNumbers, departureTimes
            ));
        }

        return matches;
    }

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
            segment = copyStops(fullRoute.subList(sourceIdx, destIdx + 1));
        } else {
            segment = copyStops(fullRoute.subList(destIdx, sourceIdx + 1));
            Collections.reverse(segment);
        }

        double baseDistance = segment.get(0).getDistanceFromStartKm();
        for (RouteStop stop : segment) {
            stop.setDistanceFromStartKm(Math.abs(stop.getDistanceFromStartKm() - baseDistance));
        }
        return segment;
    }

    /**
     * Rebuilds stops.json from EVERY currently loaded route (legacy plus every
     * admin-added one), not just whichever route was loaded most recently.
     * This is what the passenger search page's autocomplete reads, so any
     * route that's missing here is a route passengers can never search for.
     */
    private void updateStopsJson() {
        try {
            // TreeSet with case-insensitive ordering: de-dupes stop names that
            // appear on more than one route (e.g. a shared junction town),
            // and keeps the list alphabetical for the dropdown.
            Set<String> stopNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (List<RouteStop> stops : routeCache.values()) {
                for (RouteStop stop : stops) {
                    stopNames.add(stop.getStopName());
                }
            }

            Map<String, Object> json = new HashMap<>();
            json.put("stops", new ArrayList<>(stopNames));

            File file = new File(STOPS_JSON_PATH);
            file.getParentFile().mkdirs();

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, json);
            System.out.println("Updated stops.json file (" + stopNames.size() + " stops across "
                    + routeCache.size() + " route(s))");
        } catch (Exception e) {
            System.err.println("Error updating stops.json file");
            e.printStackTrace();
        }
    }

    /** Deep-copies each RouteStop so callers (and repeated calls of this
     *  method, e.g. once per route from findRoutesBetweenDepots) can freely
     *  mutate the returned segment's distances without corrupting the
     *  shared routeCache entries every other caller reads from. Fixes a
     *  latent bug where sliceBetween used to rewrite cached stops in place. */
    private List<RouteStop> copyStops(List<RouteStop> source) {
        List<RouteStop> copies = new ArrayList<>(source.size());
        for (RouteStop original : source) {
            RouteStop copy = new RouteStop();
            copy.setStopOrder(original.getStopOrder());
            copy.setStopName(original.getStopName());
            copy.setLatitude(original.getLatitude());
            copy.setLongitude(original.getLongitude());
            copy.setDistanceFromStartKm(original.getDistanceFromStartKm());
            copy.setSlackTimeMin(original.getSlackTimeMin());
            copies.add(copy);
        }
        return copies;
    }

    private boolean isAnyCellMissing(Row row, int... idxs) {
        for (int idx : idxs) {
            Cell c = row.getCell(idx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (c == null) return true;
        }
        return false;
    }

    public void removeRoute(String routeCode) {
        routeCache.remove(routeCode);
        // Deleted route's stops must disappear from the passenger search too.
        updateStopsJson();
    }
}