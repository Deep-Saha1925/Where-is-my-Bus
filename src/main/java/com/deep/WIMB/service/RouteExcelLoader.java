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

            try {
                RouteStop stop = new RouteStop();
                stop.setStopOrder((int) getNumericValue(r.getCell(0)));
                stop.setStopName(getStringValue(r.getCell(1)).trim());
                stop.setLatitude(getNumericValue(r.getCell(2)));
                stop.setLongitude(getNumericValue(r.getCell(3)));
                stop.setDistanceFromStartKm(getNumericValue(r.getCell(4)));
                stop.setSlackTimeMin((int) getNumericValue(r.getCell(5)));
                stops.add(stop);
            } catch (Exception e) {
                // Excel row numbers are 1-based and include the header, so the
                // sheet row the admin sees is i + 1.
                throw new RuntimeException("Row " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        return stops;
    }

    /**
     * Reads a numeric value from a cell regardless of whether Excel stored it
     * as an actual number or as text (common when a sheet is exported from
     * CSV/Google Sheets, or a column got formatted as Text). Only throws if
     * the cell's content genuinely isn't a number.
     */
    // Matches text like "5:30", "00:05:00", "12:00:00" — the text Excel
    // leaves behind when a numeric value was typed into a Time-formatted
    // cell (e.g. slack_min "5" becomes the literal text "00:05:00").
    private static final java.util.regex.Pattern TIME_LIKE_PATTERN =
            java.util.regex.Pattern.compile("^(\\d{1,3}):(\\d{1,2})(?::(\\d{1,2}))?$");

    private double getNumericValue(Cell cell) {
        switch (cell.getCellType()) {
            case NUMERIC:
                return cell.getNumericCellValue();
            case STRING:
                String raw = cell.getStringCellValue().trim();
                try {
                    return Double.parseDouble(raw);
                } catch (NumberFormatException e) {
                    java.util.regex.Matcher timeMatch = TIME_LIKE_PATTERN.matcher(raw);
                    if (timeMatch.matches()) {
                        // Interpret as HH:MM[:SS] and convert to total minutes,
                        // since a stray time-formatted cell is almost always a
                        // duration value, not an actual clock time.
                        int hours = Integer.parseInt(timeMatch.group(1));
                        int minutes = Integer.parseInt(timeMatch.group(2));
                        int seconds = timeMatch.group(3) != null ? Integer.parseInt(timeMatch.group(3)) : 0;
                        return hours * 60 + minutes + seconds / 60.0;
                    }
                    throw new RuntimeException("expected a number in column "
                            + (char) ('A' + cell.getColumnIndex()) + " but found \"" + raw + "\"");
                }
            case FORMULA:
                // Fall back to POI's cached evaluated value for the formula.
                return cell.getNumericCellValue();
            default:
                throw new RuntimeException("expected a number in column "
                        + (char) ('A' + cell.getColumnIndex()) + " but the cell is empty/unsupported");
        }
    }

    /**
     * Reads a text value from a cell even if Excel happens to have stored it
     * as a number (e.g. a stop name that's all digits).
     */
    private String getStringValue(Cell cell) {
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf(cell.getNumericCellValue());
            default:
                return cell.toString();
        }
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