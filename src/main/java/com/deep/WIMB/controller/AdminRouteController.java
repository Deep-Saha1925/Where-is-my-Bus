package com.deep.WIMB.controller;

import com.deep.WIMB.model.Route;
import com.deep.WIMB.repository.RouteRepository;
import com.deep.WIMB.service.RouteExcelLoader;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/admin/routes")
@RequiredArgsConstructor
public class AdminRouteController {

    private final RouteRepository routeRepository;
    private final RouteExcelLoader routeExcelLoader;

    @Value("${wimb.route.dir}")
    private String routeDir;

    private static final List<String> ALLOWED_EXTENSIONS = List.of(".xlsx", ".xls", ".xlsm");

    @GetMapping
    public List<Route> listRoutes() {
        return routeExcelLoader.getAllRoutes();
    }

    /** Parses a comma-separated "bus numbers" field from the admin form into
     *  a clean list — trims each entry, drops blanks, keeps original casing
     *  since plate formats vary. Optional: a blank/absent field just means
     *  no buses are registered yet for this route. */
    private List<String> parseBusNumbers(String raw) {
        if (raw == null || raw.isBlank()) return new ArrayList<>();
        List<String> result = new ArrayList<>();
        for (String piece : raw.split(",")) {
            String trimmed = piece.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadRoute(
            @RequestParam("routeCode") String routeCode,
            @RequestParam("routeName") String routeName,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "busNumbers", required = false) String busNumbers
    ) {
        String code = routeCode == null ? "" : routeCode.trim().toUpperCase(Locale.ROOT);
        String name = routeName == null ? "" : routeName.trim();

        if (code.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Route code is required"));
        }
        if (name.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Route name is required"));
        }
        if (routeRepository.existsByRouteCode(code)) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "A route with code \"" + code + "\" already exists. Editing/replacing isn't supported yet."));
        }
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file selected"));
        }

        String filename = file.getOriginalFilename();
        String extension = ALLOWED_EXTENSIONS.stream()
                .filter(ext -> filename != null && filename.toLowerCase(Locale.ROOT).endsWith(ext))
                .findFirst()
                .orElse(null);

        if (extension == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Please upload an Excel file (.xlsx, .xls, or .xlsm)"
            ));
        }

        try {
            byte[] fileBytes = file.getBytes();

            Route route = new Route();
            route.setRouteCode(code);
            route.setRouteName(name);
            // filePath is now purely informational (shown in the admin table);
            // the actual content lives in fileData below, which is what's
            // used for loading — so this never breaks even if disk writes fail.
            route.setFilePath(routeDir + "/" + code + extension);
            route.setFileData(fileBytes);
            route.setUploadedAt(LocalDateTime.now());
            route.setBusNumbers(parseBusNumbers(busNumbers));

            // Best-effort disk copy too — harmless if it works, and if this
            // container's disk gets wiped on the next restart, it doesn't
            // matter, since loading reads from fileData above, not from here.
            try {
                File dest = new File(routeDir, code + extension);
                dest.getParentFile().mkdirs();
                Files.write(dest.toPath(), fileBytes);
            } catch (Exception diskEx) {
                System.err.println("Note: could not also write route file to disk (non-fatal): " + diskEx.getMessage());
            }

            routeExcelLoader.loadRouteFromDisk(route);
            route.setStopCount(routeExcelLoader.getStopCount(code));

            routeRepository.save(route);

            return ResponseEntity.ok(Map.of(
                    "message", "Route added successfully",
                    "routeCode", code,
                    "stopCount", route.getStopCount()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Route");

            Row header = sheet.createRow(0);
            String[] headers = {"stop_order", "stop_name", "latitude", "longitude", "distance_km", "slack_min"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            Object[][] examples = {
                    {1, "Alipurduar", 26.4799, 89.5355, 0.0, 0},
                    {2, "Alipurduar Chowpathy", 26.48083, 89.526, 1.5, 2},
                    {3, "Sonapur", 26.494, 89.368, 10.5, 2},
                    {4, "Falakata", 26.51721, 89.20694, 15.0, 0}
            };
            for (int i = 0; i < examples.length; i++) {
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue((Integer) examples[i][0]);
                row.createCell(1).setCellValue((String) examples[i][1]);
                row.createCell(2).setCellValue((Double) examples[i][2]);
                row.createCell(3).setCellValue((Double) examples[i][3]);
                row.createCell(4).setCellValue((Double) examples[i][4]);
                row.createCell(5).setCellValue((Integer) examples[i][5]);
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=route-template.xlsx")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        }
    }

    @DeleteMapping("/{routeCode}")
    public ResponseEntity<?> deleteRoute(@PathVariable String routeCode) {
        String code = routeCode.trim().toUpperCase(Locale.ROOT);
        Route route = routeRepository.findByRouteCode(code).orElse(null);

        if (route == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Route not found: " + code));
        }

        // Pull it out of the in-memory cache first, so no new ride can be
        // started against it the instant this request is processed.
        routeExcelLoader.removeRoute(code);

        // Best-effort disk cleanup too — not required, since fileData in the
        // DB (already removed via routeExcelLoader.removeRoute + repository
        // delete below) was the actual source of truth, not this file.
        try {
            File file = new File(route.getFilePath().replace('\\', '/'));
            if (file.exists() && !file.delete()) {
                System.err.println("Note: could not delete route file on disk (non-fatal): " + file.getPath());
            }
        } catch (Exception ignored) {
            // Disk cleanup is a nicety, not a requirement — never fail the delete over it.
        }

        routeRepository.delete(route);

        return ResponseEntity.ok(Map.of("message", "Route \"" + code + "\" deleted"));
    }

    @PutMapping("/{routeCode}")
    public ResponseEntity<?> updateRoute(
            @PathVariable String routeCode,
            @RequestParam("routeName") String routeName,
            @RequestParam(value = "file", required = false) MultipartFile file
    ) {
        String code = routeCode.trim().toUpperCase(Locale.ROOT);
        String name = routeName == null ? "" : routeName.trim();

        if (name.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Route name is required"));
        }

        Route route = routeRepository.findByRouteCode(code).orElse(null);
        if (route == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Route not found: " + code));
        }

        route.setRouteName(name);

        // File is optional here — only touch it if the admin actually chose one
        if (file != null && !file.isEmpty()) {
            String filename = file.getOriginalFilename();
            boolean validExtension = filename != null &&
                    ALLOWED_EXTENSIONS.stream().anyMatch(ext -> filename.toLowerCase(Locale.ROOT).endsWith(ext));

            if (!validExtension) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Please upload an Excel file (.xlsx, .xls, or .xlsm)"
                ));
            }

            try {
                byte[] fileBytes = file.getBytes();
                route.setFileData(fileBytes);

                // Best-effort disk copy — same reasoning as upload: nice to
                // have for debugging, never required for the app to work.
                try {
                    File dest = new File(route.getFilePath().replace('\\', '/'));
                    if (dest.getParentFile() != null) dest.getParentFile().mkdirs();
                    Files.write(dest.toPath(), fileBytes);
                } catch (Exception diskEx) {
                    System.err.println("Note: could not also write route file to disk (non-fatal): " + diskEx.getMessage());
                }

                routeExcelLoader.loadRouteFromDisk(route); // reload into cache immediately
                route.setStopCount(routeExcelLoader.getStopCount(code));
            } catch (Exception e) {
                return ResponseEntity.status(500).body(Map.of("error", "Failed to update route file: " + e.getMessage()));
            }
        }

        routeRepository.save(route);

        return ResponseEntity.ok(Map.of(
                "message", "Route \"" + code + "\" updated",
                "stopCount", route.getStopCount()
        ));
    }
}