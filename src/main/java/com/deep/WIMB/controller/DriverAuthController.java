package com.deep.WIMB.controller;

import com.deep.WIMB.service.DriverAccessService;
import com.deep.WIMB.service.DriverTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/driver")
@RequiredArgsConstructor
public class DriverAuthController {

    private final DriverAccessService driverAccessService;
    private final DriverTokenService driverTokenService;

    @GetMapping("/depots")
    public List<String> getDepots() {
        return driverAccessService.getDepotNames();
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody Map<String, String> body) {
        String depotName = body.get("depotName");
        String code      = body.get("code");
        String busNumber = body.get("busNumber");

        if (busNumber == null || busNumber.isBlank()) {
            return ResponseEntity.status(400).body(Map.of("error", "Bus number is required"));
        }
        if (!driverAccessService.isValid(depotName, code)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid depot or code"));
        }

        return ResponseEntity.ok(Map.of("token", driverTokenService.issueToken(busNumber)));
    }
}