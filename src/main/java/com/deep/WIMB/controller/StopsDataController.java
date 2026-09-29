package com.deep.WIMB.controller;

import com.deep.WIMB.service.RouteExcelLoader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Serves the passenger page's stop autocomplete list live from the loaded
 * routes. RouteExcelLoader used to (re)write ./data/stops.json on disk, but
 * static files are served from the classpath (src/main/resources/static or
 * the packaged jar), never from ./data -- so newly uploaded routes' stops
 * never appeared in the passenger search. Spring MVC handler mappings take
 * priority over static resources, so this overrides the stale file.
 */
@RestController
public class StopsDataController {

    private final RouteExcelLoader loader;

    public StopsDataController(RouteExcelLoader loader) {
        this.loader = loader;
    }

    @GetMapping("/data/stops.json")
    public Map<String, List<String>> stops() {
        return Map.of("stops", loader.getAllStopNames());
    }
}
