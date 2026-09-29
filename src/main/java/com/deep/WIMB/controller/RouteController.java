package com.deep.WIMB.controller;

import com.deep.WIMB.dto.DepotRouteMatch;
import com.deep.WIMB.dto.RouteStop;
import com.deep.WIMB.dto.RouteSummary;
import com.deep.WIMB.service.RouteExcelLoader;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final RouteExcelLoader loader;

    public RouteController(RouteExcelLoader loader){
        this.loader = loader;
    }

    // Unchanged behavior when routeCode is omitted — still resolves against
    // the legacy single route, exactly as before. When a driver has picked
    // one of the newly-admin-uploaded routes, routeCode scopes the lookup
    // to that specific route instead.
    @GetMapping("")
    public List<RouteStop> getRoute(
            @RequestParam String source,
            @RequestParam String destination,
            @RequestParam(required = false) String routeCode
    ) {
        if (routeCode != null && !routeCode.isBlank()) {
            return loader.getRouteBetween(routeCode, source, destination);
        }
        return loader.getRouteBetween(source, destination);
    }

    // Public list for the driver's route picker — code + name + stop count only,
    // never the server file path (that stays admin-only via /admin/routes).
    @GetMapping("/list")
    public List<RouteSummary> listRoutes() {
        return loader.getAllRoutes().stream()
                // Live stop count from the loaded route, not the value saved at upload time
                // (which goes stale when the route file is replaced or fails to load).
                .map(r -> new RouteSummary(r.getRouteCode(), r.getRouteName(), loader.getStopCount(r.getRouteCode())))
                // A route whose file failed to load has no stops — never offer it to drivers.
                .filter(r -> r.getStopCount() > 0)
                .toList();
    }

    // All stops for one route, in order — used to populate the source/destination
    // pickers once a driver has selected their route.
    @GetMapping("/stops")
    public List<RouteStop> getRouteStops(@RequestParam String routeCode) {
        try {
            return loader.getFullRoute(routeCode);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    // Splits "SOURCE_DESTINATION" correctly even when stop names contain "_".
    @GetMapping("/split")
    public Map<String, String> splitRouteKey(
            @RequestParam String routeKey,
            @RequestParam(required = false) String routeCode
    ) {
        String[] parts = loader.splitRouteKey(routeCode, routeKey);
        if (parts == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid routeKey format: " + routeKey);
        }
        return Map.of("source", parts[0], "destination", parts[1]);
    }

    // Static/non-live "does a service exist between these two depots" search.
    // Unlike GET /api/ride/active, this never looks at GPS or live rides —
    // it answers from admin-registered routes + bus rosters, so it still
    // gives a useful answer even when nothing is on the road right now.
    @GetMapping("/by-depots")
    public List<DepotRouteMatch> getRoutesBetweenDepots(
            @RequestParam String source,
            @RequestParam String destination
    ) {
        return loader.findRoutesBetweenDepots(source, destination);
    }
}