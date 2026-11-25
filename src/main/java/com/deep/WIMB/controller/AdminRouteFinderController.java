/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.controller;

import com.deep.WIMB.service.RouteFinderService;
import com.deep.WIMB.service.RouteFinderService.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Admin side of the Route Finder tab: the admin can see and change every route. */
@RestController
@RequestMapping("/admin/route-finder")
@RequiredArgsConstructor
public class AdminRouteFinderController {

    private final RouteFinderService finder;

    public record SaveStopsRequest(List<RouteFinderService.StopInput> stops, boolean alsoReturn) {}

    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam(value = "from", defaultValue = "") String from,
                                            @RequestParam(value = "to", defaultValue = "") String to) {
        return finder.search(from, to, Actor.asAdmin());
    }

    @GetMapping("/{routeCode}/stops")
    public Map<String, Object> detail(@PathVariable String routeCode) {
        return finder.detail(routeCode, Actor.asAdmin());
    }

    @PutMapping("/{routeCode}/stops")
    public Map<String, Object> saveStops(@PathVariable String routeCode, @RequestBody SaveStopsRequest body) {
        return finder.saveStops(routeCode, body.stops(), body.alsoReturn(), Actor.asAdmin());
    }

    @PutMapping("/{routeCode}/details")
    public Map<String, Object> saveDetails(@PathVariable String routeCode,
                                           @RequestBody RouteFinderService.DetailsInput body) {
        return finder.saveDetails(routeCode, body, Actor.asAdmin());
    }
}
