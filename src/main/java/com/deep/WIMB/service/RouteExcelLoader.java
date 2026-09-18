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
    private final DriverAccessService driverAccessService;

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
            System.out.println("Loaded legacy route " + legacyRouteKey + " with " + stops.size()
                    + " stops: " + stopNamesOf(stops));
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
                backfillDirectionIfMissing(route);
            } catch (Exception e) {
                // A route that fails to load here is invisible to every search
                // and every driver for the rest of the process's life, so this
                // deserves more than a one-line message with no context.
                System.err.println("FAILED to load route " + route.getRouteCode()
                        + " (" + route.getRouteName() + ") — it will NOT appear in any search "
                        + "until its Excel file is fixed and re-uploaded. Reason: " + e.getMessage());
            }
        }
    }

    /**
     * Fills in sourceDepot/destinationDepot for a route that doesn't have
     * them yet, by reading the route's NAME rather than its stop order.
     *
     * This matters because a route's stop order can't reliably tell you its
     * direction (see findRoutesBetweenDepots for why), but the admin's own
     * naming convention already does: a route named "Alipurduar_CoochBehar"
     * or "Alipurduar \u2192 Cooch Behar" or "Alipurduar to Cooch Behar" was
     * clearly created to mean "runs from Alipurduar to Cooch Behar" -- that's
     * exactly the metadata the depot search needs, just sitting in a
     * different field than expected. This lets every already-registered
     * route pick up the right direction automatically, with nothing to
     * re-enter by hand, as long as its name follows that pattern.
     *
     * Only ever fills in what's currently blank; never overwrites a value
     * the admin has already set (deliberately or via the Update form).
     */
    private void backfillDirectionIfMissing(Route route) {
        boolean hasSource = route.getSourceDepot() != null && !route.getSourceDepot().isBlank();
        boolean hasDest = route.getDestinationDepot() != null && !route.getDestinationDepot().isBlank();
        if (hasSource && hasDest) return;

        String[] guess = deriveDepotsFromRouteName(route.getRouteName());
        if (guess == null) {
            System.out.println("Route " + route.getRouteCode() + " (\"" + route.getRouteName()
                    + "\") has no direction set and none could be guessed from its name "
                    + "-- set it manually via Update in the Routes tab.");
            return;
        }

        if (!hasSource) route.setSourceDepot(guess[0]);
        if (!hasDest) route.setDestinationDepot(guess[1]);
        routeRepository.save(route);
        System.out.println("Route " + route.getRouteCode() + " (\"" + route.getRouteName()
                + "\") direction auto-set from its name: " + guess[0] + " -> " + guess[1]);
    }

    /**
     * Splits a route name on common separators and matches each half against
     * the registered depot list, so "Alipurduar_CoochBehar",
     * "Alipurduar \u2192 Cooch Behar", "Alipurduar-CoochBehar" and
     * "Alipurduar to Cooch Behar" are all recognised.
     *
     * @return [sourceDepot, destinationDepot] using the depot list's own
     *         spelling, or null if the name couldn't be confidently split
     *         into two distinct, recognisable depots.
     */
    private String[] deriveDepotsFromRouteName(String routeName) {
        if (routeName == null || routeName.isBlank()) return null;

        String[] parts = routeName.split("_|\u2194|\u21C4|\u2192|->|\\bto\\b|/", 2);
        if (parts.length != 2) return null;

        List<String> depotNames = driverAccessService.getDepotNames();
        String source = matchDepotName(parts[0], depotNames);
        String destination = matchDepotName(parts[1], depotNames);

        if (source == null || destination == null) return null;
        if (normalizeName(source).equals(normalizeName(destination))) return null;

        return new String[]{source, destination};
    }

    /** Finds the registered depot name whose normalized form best matches
     *  (exactly, then by containment) the given fragment of a route name. */
    private String matchDepotName(String fragment, List<String> depotNames) {
        String key = normalizeName(fragment);
        if (key.isEmpty()) return null;

        for (String depot : depotNames) {
            if (normalizeName(depot).equals(key)) return depot;
        }
        if (key.length() < MIN_PARTIAL_MATCH_LENGTH) return null;
        for (String depot : depotNames) {
            String depotKey = normalizeName(depot);
            if (depotKey.length() >= MIN_PARTIAL_MATCH_LENGTH && (depotKey.contains(key) || key.contains(depotKey))) {
                return depot;
            }
        }
        return null;
    }

    /** Re-runs the name-based direction guess for every route that's still
     *  missing it, on demand -- lets an admin apply this to already-loaded
     *  routes immediately, without waiting for (or forcing) a restart. */
    public Map<String, String> backfillAllMissingDirections() {
        Map<String, String> results = new LinkedHashMap<>();
        for (Route route : routeRepository.findAll()) {
            boolean hasSource = route.getSourceDepot() != null && !route.getSourceDepot().isBlank();
            boolean hasDest = route.getDestinationDepot() != null && !route.getDestinationDepot().isBlank();
            if (hasSource && hasDest) continue;

            String[] guess = deriveDepotsFromRouteName(route.getRouteName());
            if (guess == null) {
                results.put(route.getRouteCode(), "could not guess from name \"" + route.getRouteName() + "\"");
                continue;
            }
            if (!hasSource) route.setSourceDepot(guess[0]);
            if (!hasDest) route.setDestinationDepot(guess[1]);
            routeRepository.save(route);
            results.put(route.getRouteCode(), "set to " + guess[0] + " -> " + guess[1]);
        }
        return results;
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
                    + " (" + route.getRouteName() + ") with " + stops.size() + " stops: "
                    + stopNamesOf(stops));
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

    /** First stop name in this route's own sheet, or "" if not loaded/empty.
     *  Used only as an initial guess for a route's source depot when the
     *  admin hasn't stated one explicitly — never as the actual direction
     *  signal for matching (see findRoutesBetweenDepots for why). */
    public String getFirstStopName(String routeCode) {
        List<RouteStop> stops = routeCache.get(routeCode);
        return (stops == null || stops.isEmpty()) ? "" : stops.get(0).getStopName();
    }

    /** Last stop name in this route's own sheet — see getFirstStopName. */
    public String getLastStopName(String routeCode) {
        List<RouteStop> stops = routeCache.get(routeCode);
        return (stops == null || stops.isEmpty()) ? "" : stops.get(stops.size() - 1).getStopName();
    }

    public int getStopOrderByName(String routeCode, String stopName) {
        List<RouteStop> fullRoute = getFullRoute(routeCode);
        int idx = findStopIndex(fullRoute, stopName);
        if (idx == -1) {
            throw new RuntimeException("Stop not found on route " + routeCode + ": " + stopName);
        }
        return fullRoute.get(idx).getStopOrder();
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
        System.out.println("Depot search: \"" + source + "\" -> \"" + destination
                + "\" across " + routeCache.size() + " loaded route(s): " + routeCache.keySet());

        for (Map.Entry<String, List<RouteStop>> entry : routeCache.entrySet()) {
            String routeCode = entry.getKey();
            List<RouteStop> fullRoute = entry.getValue();

            Route route = routeCode.equals(legacyRouteKey)
                    ? null // the legacy demo route has no DB row/metadata at all
                    : routeRepository.findByRouteCode(routeCode).orElse(null);
            if (route == null && !routeCode.equals(legacyRouteKey)) continue; // shouldn't happen, but stay safe

            // Direction is decided by the admin-entered sourceDepot/destinationDepot
            // fields, NOT by row order in the route's Excel sheet. Two routes
            // covering the same physical road commonly list their stops in
            // the exact same order (the corridor doesn't change, only which
            // way the bus runs does) -- so row order can't tell "the A->B
            // service" apart from "the B->A service" on that same road. The
            // legacy demo route predates this field and has none, so it's
            // left bidirectional, matching either way as it always has.
            if (route != null && route.getSourceDepot() != null && !route.getSourceDepot().isBlank()) {
                boolean forwardMatch = normalizeName(route.getSourceDepot()).equals(normalizeName(source))
                        && normalizeName(route.getDestinationDepot()).equals(normalizeName(destination));
                if (!forwardMatch) {
                    System.out.println("Depot search: route " + routeCode + " skipped — registered as \""
                            + route.getSourceDepot() + " -> " + route.getDestinationDepot()
                            + "\", which doesn't match this search");
                    continue;
                }
            }

            int sourceIdx = findStopIndex(fullRoute, source);
            int destIdx = findStopIndex(fullRoute, destination);

            if (sourceIdx == -1 || destIdx == -1) {
                // Log the reason rather than failing silently. When a depot
                // search comes back empty it's almost always because the
                // depot name and the route's stop names are spelled
                // differently, and without this line there's nothing
                // anywhere to tell you that.
                String missing = sourceIdx == -1
                        ? (destIdx == -1 ? "\"" + source + "\" and \"" + destination + "\"" : "\"" + source + "\"")
                        : "\"" + destination + "\"";
                System.out.println("Depot search: route " + routeCode + " skipped — stop not on this route: "
                        + missing + ". This route's stops are: " + stopNamesOf(fullRoute));
                continue;
            }
            if (sourceIdx == destIdx) {
                continue; // source and destination resolve to the same stop on this route
            }

            // Slicing itself stays direction-agnostic: it just needs to find
            // both stops and hand back the segment between them, in the order
            // the passenger asked for, regardless of which way they happen to
            // sit in the underlying sheet. The direction FILTER above is what
            // decides whether this route was even allowed to reach this point.
            List<RouteStop> segment = sourceIdx < destIdx
                    ? copyStops(fullRoute.subList(sourceIdx, destIdx + 1))
                    : reversedCopy(fullRoute.subList(destIdx, sourceIdx + 1));
            double baseDistance = segment.get(0).getDistanceFromStartKm();
            for (RouteStop stop : segment) {
                stop.setDistanceFromStartKm(Math.abs(stop.getDistanceFromStartKm() - baseDistance));
            }

            String routeName;
            List<String> busNumbers;
            List<String> departureTimes;
            if (route == null) {
                routeName = "Alipurduar \u21C4 Falakata (default route)";
                busNumbers = Collections.emptyList();
                departureTimes = Collections.emptyList();
            } else {
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

        if (matches.isEmpty()) {
            System.out.println("Depot search: no route connects \"" + source + "\" and \"" + destination
                    + "\". Check that both names appear as stop_name values in a route's Excel sheet.");
        }
        return matches;
    }

    /**
     * Reads a number out of a cell regardless of how Excel stored it.
     *
     * getNumericCellValue() throws outright on a text cell, and a latitude
     * typed as "26.4799" (or pasted with a stray space, or imported as text)
     * is stored as a STRING. That single throw used to escape all the way out
     * of parseWorkbook and abort the whole route load, which is why one badly
     * typed cell made an entire route disappear from the system.
     *
     * @return the value, or null if the cell is blank or genuinely unparseable.
     */
    private Double readNumeric(Cell cell, DataFormatter formatter) {
        if (cell == null) return null;

        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType()
                : cell.getCellType();

        if (type == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }

        String text = formatter.formatCellValue(cell)
                .replace('\u00A0', ' ')
                .replace(',', '.')
                .trim();
        if (text.isEmpty()) return null;

        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /** Text of a cell, whatever its underlying type, with invisible junk removed. */
    private String readText(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        return formatter.formatCellValue(cell)
                .replace('\u00A0', ' ')
                .replace('\u200B', ' ')
                .replace('\uFEFF', ' ')
                .trim();
    }

    /**
     * Parses a route sheet into stops. A row is only usable if it has a stop
     * name plus a latitude and longitude — those three are what the route is
     * actually built from. stop_order, distance_km and slack_min are filled
     * in with sensible defaults when missing, rather than disqualifying the
     * row, since a missing slack value is not a reason to lose a stop.
     *
     * A row that can't be used is skipped with a reason; the rest of the
     * sheet still loads.
     */
    private List<RouteStop> parseWorkbook(Workbook wb) {
        Sheet sheet = wb.getSheetAt(0);
        DataFormatter formatter = new DataFormatter();
        List<RouteStop> stops = new ArrayList<>();

        for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
            Row r = sheet.getRow(i);
            if (r == null) continue;

            String name = readText(r.getCell(1), formatter);
            Double latitude = readNumeric(r.getCell(2), formatter);
            Double longitude = readNumeric(r.getCell(3), formatter);

            if (name.isEmpty() && latitude == null && longitude == null) {
                continue; // blank spacer row — not worth a warning
            }
            if (name.isEmpty()) {
                System.out.println("  Skipping row " + (i + 1) + ": no stop_name");
                continue;
            }
            if (latitude == null || longitude == null) {
                System.out.println("  Skipping row " + (i + 1) + " (\"" + name
                        + "\"): latitude/longitude missing or not a number");
                continue;
            }

            Double order = readNumeric(r.getCell(0), formatter);
            Double distance = readNumeric(r.getCell(4), formatter);
            Double slack = readNumeric(r.getCell(5), formatter);

            RouteStop stop = new RouteStop();
            stop.setStopOrder(order == null ? stops.size() + 1 : (int) order.doubleValue());
            stop.setStopName(name);
            stop.setLatitude(latitude);
            stop.setLongitude(longitude);
            stop.setDistanceFromStartKm(distance == null ? 0.0 : distance);
            stop.setSlackTimeMin(slack == null ? 0 : (int) slack.doubleValue());
            stops.add(stop);
        }
        return stops;
    }

    /**
     * Collapses a place name down to a comparison key: lowercase, letters and
     * digits only, everything else dropped.
     *
     * This exists because the two names being compared come from two
     * completely separate spreadsheets that nobody ever reconciled. Depot
     * names come from depot-codes.xlsx and are typed like "COOCHBEHAR";
     * stop names come from each route's own sheet and are typed like
     * "Cooch Behar". equalsIgnoreCase handles the capitals but not the
     * space, so every depot search against a two-word town silently found
     * nothing. Normalising both sides makes "COOCHBEHAR", "Cooch Behar",
     * "CoochBehar" and "cooch-behar" all the same key.
     */
    private static String normalizeName(String name) {
        if (name == null) return "";
        StringBuilder key = new StringBuilder(name.length());
        for (char c : name.toCharArray()) {
            if (Character.isLetterOrDigit(c)) key.append(Character.toLowerCase(c));
        }
        return key.toString();
    }

    /** Shortest normalized length before partial matching is allowed. Below
     *  this, a containment check is more likely to produce a wrong match than
     *  a right one (e.g. a depot called "OLD" inside "GOLDEN MARKET"). */
    private static final int MIN_PARTIAL_MATCH_LENGTH = 5;

    /**
     * Locates a stop by name within a route, tolerantly. Tries an exact
     * normalized match across every stop first, and only if nothing matched
     * falls back to partial matching — so a depot named "COOCHBEHAR" still
     * resolves against a stop written as "Cooch Behar Bus Terminus". The
     * shortest candidate wins the fallback, since that's the closest match.
     *
     * @return the index into fullRoute, or -1 if the name isn't on this route.
     */
    private int findStopIndex(List<RouteStop> fullRoute, String wanted) {
        String key = normalizeName(wanted);
        if (key.isEmpty()) return -1;

        for (int i = 0; i < fullRoute.size(); i++) {
            if (normalizeName(fullRoute.get(i).getStopName()).equals(key)) return i;
        }

        if (key.length() < MIN_PARTIAL_MATCH_LENGTH) return -1;

        int best = -1;
        int bestLength = Integer.MAX_VALUE;
        for (int i = 0; i < fullRoute.size(); i++) {
            String candidate = normalizeName(fullRoute.get(i).getStopName());
            if (candidate.length() < MIN_PARTIAL_MATCH_LENGTH) continue;

            if (candidate.contains(key) || key.contains(candidate)) {
                if (candidate.length() < bestLength) {
                    best = i;
                    bestLength = candidate.length();
                }
            }
        }
        return best;
    }

    private List<RouteStop> sliceBetween(List<RouteStop> fullRoute, String source, String destination) {
        int sourceIdx = findStopIndex(fullRoute, source);
        int destIdx   = findStopIndex(fullRoute, destination);

        if (sourceIdx == -1 || destIdx == -1) {
            // Name the side that actually failed — "Invalid source or
            // destination" gave no clue which of the two was the problem,
            // or what this route does contain.
            String missing = sourceIdx == -1
                    ? (destIdx == -1 ? "\"" + source + "\" and \"" + destination + "\"" : "\"" + source + "\"")
                    : "\"" + destination + "\"";
            throw new RuntimeException("Stop not on this route: " + missing);
        }
        if (sourceIdx == destIdx) {
            throw new RuntimeException("Source and destination resolve to the same stop");
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
    /** Comma-joined stop names, for log messages that need to show what a
     *  route actually contains rather than just how many stops it has. */
    private String stopNamesOf(List<RouteStop> stops) {
        List<String> names = new ArrayList<>(stops.size());
        for (RouteStop stop : stops) names.add(stop.getStopName());
        return String.join(", ", names);
    }

    /** Deep-copies a sub-list in reverse order — used when a route's stored
     *  stop order runs the opposite way from what the passenger asked for. */
    private List<RouteStop> reversedCopy(List<RouteStop> source) {
        List<RouteStop> copies = copyStops(source);
        Collections.reverse(copies);
        return copies;
    }

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

    public void removeRoute(String routeCode) {
        routeCache.remove(routeCode);
        // Deleted route's stops must disappear from the passenger search too.
        updateStopsJson();
    }
}