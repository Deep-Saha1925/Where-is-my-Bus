package com.deep.WIMB.controller;

import com.deep.WIMB.dto.RouteStop;
import com.deep.WIMB.dto.RouteSummary;
import com.deep.WIMB.service.RouteExcelLoader;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
                .map(r -> new RouteSummary(r.getRouteCode(), r.getRouteName(), r.getStopCount()))
                .toList();
    }

    // All stops for one route, in order — used to populate the source/destination
    // pickers once a driver has selected their route.
    @GetMapping("/stops")
    public List<RouteStop> getRouteStops(@RequestParam String routeCode) {
        return loader.getFullRoute(routeCode);
    }
}