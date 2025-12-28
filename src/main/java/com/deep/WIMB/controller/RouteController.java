package com.deep.WIMB.controller;

import com.deep.WIMB.dto.RouteStop;
import com.deep.WIMB.service.RouteExcelLoader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final RouteExcelLoader loader;

    public RouteController(RouteExcelLoader loader){
        this.loader = loader;
    }

    @GetMapping("/{routeKey}")
    public List<RouteStop> getRoute(@PathVariable String routeKey){
        return loader.getRoute(routeKey);
    }

}
