/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.controller;

import com.deep.WIMB.service.DepotService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Depot staff pages. /depot/** is ROLE_DEPOT only (see SecurityConfig).
 * The depot is always taken from the logged-in user, never from a request parameter,
 * so one depot can never ask for another depot's buses.
 */
@Controller
@RequestMapping("/depot")
@RequiredArgsConstructor
public class DepotController {

    private final DepotService depotService;

    @GetMapping("/dashboard")
    public String dashboard() {
        return "depot-dashboard";
    }

    @GetMapping("/api/buses")
    @ResponseBody
    public Map<String, Object> myBuses(Authentication auth) {
        String depot = auth.getName().substring(DepotService.LOGIN_PREFIX.length());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("depot", depot);
        out.put("buses", depotService.liveBuses(depot));
        return out;
    }
}
