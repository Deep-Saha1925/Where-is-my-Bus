/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.controller;

import com.deep.WIMB.service.DepotService;
import com.deep.WIMB.service.RouteFinderService;
import com.deep.WIMB.service.RouteFinderService.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Depot side of the Route Finder tab. Covered by the /depot/** rule in SecurityConfig. A depot can look
 * up any route (the same data passengers see), but can only change routes it owns or that start at it;
 * the depot's identity always comes from its login.
 */
@RestController
@RequestMapping("/depot/api/route-finder")
@RequiredArgsConstructor
public class DepotRouteFinderController {

    private final RouteFinderService finder;

    public record SaveStopsRequest(List<RouteFinderService.StopInput> stops, boolean alsoReturn) {}

    private static Actor actor(Authentication auth) {
        return Actor.asDepot(auth.getName().substring(DepotService.LOGIN_PREFIX.length()));
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam(value = "from", defaultValue = "") String from,
                                            @RequestParam(value = "to", defaultValue = "") String to,
                                            Authentication auth) {
        return finder.search(from, to, actor(auth));
    }

    @GetMapping("/{routeCode}/stops")
    public Map<String, Object> detail(@PathVariable String routeCode, Authentication auth) {
        return finder.detail(routeCode, actor(auth));
    }

    @PutMapping("/{routeCode}/stops")
    public Map<String, Object> saveStops(@PathVariable String routeCode, @RequestBody SaveStopsRequest body,
                                         Authentication auth) {
        return finder.saveStops(routeCode, body.stops(), body.alsoReturn(), actor(auth));
    }

    @PutMapping("/{routeCode}/details")
    public Map<String, Object> saveDetails(@PathVariable String routeCode,
                                           @RequestBody RouteFinderService.DetailsInput body,
                                           Authentication auth) {
        return finder.saveDetails(routeCode, body, actor(auth));
    }
}
