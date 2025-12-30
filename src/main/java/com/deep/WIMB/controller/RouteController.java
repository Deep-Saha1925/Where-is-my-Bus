package com.deep.WIMB.controller;

import com.deep.WIMB.dto.RouteStop;
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

    @GetMapping("")
    public List<RouteStop> getRoute(
            @RequestParam String source,
            @RequestParam String destination
    ) {
        return loader.getRouteBetween(
                source,
                destination
        );
    }

}
