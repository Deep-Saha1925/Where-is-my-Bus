package com.deep.WIMB.controller;

import com.deep.WIMB.service.DriverAccessService;
import com.deep.WIMB.service.DriverTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/driver")
public class DriverAuthController {

    @Autowired private DriverAccessService driverAccessService;
    @Autowired private DriverTokenService driverTokenService;

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody Map<String, String> body) {
        String busNumber = body.get("busNumber");
        String code = body.get("code");

        if (!driverAccessService.isValid(busNumber, code)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid bus number or code"));
        }
        return ResponseEntity.ok(Map.of("token", driverTokenService.issueToken(busNumber)));
    }
}