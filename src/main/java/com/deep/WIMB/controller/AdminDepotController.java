package com.deep.WIMB.controller;

import com.deep.WIMB.service.DriverAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

@RestController
@RequestMapping("/admin/depots")
@RequiredArgsConstructor
public class AdminDepotController {

    private final DriverAccessService driverAccessService;

    private static final String DEPOT_FILE_PATH = "data/depot-codes.xlsx";

    @GetMapping
    public Map<String, String> viewCurrentDepots() {
        return driverAccessService.getAllDepotCodes();
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadDepotFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file selected"));
        }
        if (!file.getOriginalFilename().toLowerCase().endsWith(".xlsx")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please upload a .xlsx file"));
        }

        try {
            File dest = new File(DEPOT_FILE_PATH);
            dest.getParentFile().mkdirs(); // create the /data folder if it doesn't exist yet
            Files.write(dest.toPath(), file.getBytes());

            driverAccessService.reload();

            return ResponseEntity.ok(Map.of(
                    "message", "Depot codes updated successfully",
                    "depotCount", driverAccessService.getDepotNames().size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }
}