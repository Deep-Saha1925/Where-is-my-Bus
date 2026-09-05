package com.deep.WIMB.controller;

import com.deep.WIMB.model.Route;
import com.deep.WIMB.repository.RouteRepository;
import com.deep.WIMB.service.RouteExcelLoader;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    /** Parses a comma-separated field from the admin form into a clean list —
     *  trims each entry, drops blanks. Used for both bus numbers and
     *  departure times since the parsing rules are identical; departure
     *  times are kept as free-text (e.g. "06:00" or "6:00 AM") rather than
     *  strictly validated, since this is informational, not scheduling logic. */
    private List<String> parseCommaList(String raw) {
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
            route.setBusNumbers(parseCommaList(busNumbers));
            // Departure times are managed exclusively from the Schedule tab
            // now (manual add-one-row form or bulk file) — not here, to avoid
            // scattering the same data across multiple input surfaces.

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

    // Bulk alternative to the per-route "busNumbers" field above: upload one
    // sheet covering many routes at once. Rows are grouped by route_code, and
    // each matching route's bus list is fully replaced with that group —
    // same "whole list, not merge" semantics as the per-route field already
    // has. Routes NOT mentioned in the file are left completely untouched
    // (unlike the Depot Codes upload, which replaces everything).
    @PostMapping("/buses/upload")
    public ResponseEntity<?> uploadBusRoster(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file selected"));
        }

        String filename = file.getOriginalFilename();
        boolean validExtension = filename != null &&
                ALLOWED_EXTENSIONS.stream().anyMatch(ext -> filename.toLowerCase(Locale.ROOT).endsWith(ext));

        if (!validExtension) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Please upload an Excel file (.xlsx, .xls, or .xlsm)"
            ));
        }

        Map<String, List<String>> rosterByRoute = new LinkedHashMap<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) { // skip header row
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String code = formatter.formatCellValue(row.getCell(0)).trim().toUpperCase(Locale.ROOT);
                String bus = formatter.formatCellValue(row.getCell(1)).trim();

                if (code.isEmpty() || bus.isEmpty()) continue;
                rosterByRoute.computeIfAbsent(code, k -> new ArrayList<>()).add(bus);
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to parse file: " + e.getMessage()));
        }

        if (rosterByRoute.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "No valid rows found — expected columns: route_code, bus_number"
            ));
        }

        int updatedRoutes = 0;
        List<String> unknownCodes = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : rosterByRoute.entrySet()) {
            Route route = routeRepository.findByRouteCode(entry.getKey()).orElse(null);
            if (route == null) {
                unknownCodes.add(entry.getKey());
                continue;
            }
            route.setBusNumbers(entry.getValue());
            routeRepository.save(route);
            updatedRoutes++;
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Bus roster updated for " + updatedRoutes + " route" + (updatedRoutes == 1 ? "" : "s"));
        response.put("routesUpdated", updatedRoutes);
        if (!unknownCodes.isEmpty()) {
            response.put("warning", "These route codes weren't found and were skipped: " + String.join(", ", unknownCodes));
        }
        return ResponseEntity.ok(response);
    }

    // Generates a ready-to-edit example .xlsx for the bulk bus-roster upload above.
    @GetMapping("/buses/template")
    public ResponseEntity<byte[]> downloadBusRosterTemplate() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Bus Roster");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("route_code");
            header.createCell(1).setCellValue("bus_number");

            String[][] examples = {
                    {"APD_FLK", "WB-23A-1245"},
                    {"APD_FLK", "WB-23A-1290"},
                    {"APD_SLG", "WB-23B-0456"}
            };
            for (int i = 0; i < examples.length; i++) {
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(examples[i][0]);
                row.createCell(1).setCellValue(examples[i][1]);
            }

            sheet.setColumnWidth(0, 4000);
            sheet.setColumnWidth(1, 5000);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bus-roster-template.xlsx")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        }
    }

    // Bulk alternative to the manual add-one-row form below: upload one
    // sheet covering many routes' timetables at once. Columns are route_code,
    // route_name (informational only — not written back, just makes the
    // sheet self-describing), and departure_time. Same semantics as the
    // bus-roster bulk upload above — full replace per route_code mentioned,
    // routes not in the file are untouched.
    @PostMapping("/departures/upload")
    public ResponseEntity<?> uploadDepartureTimes(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file selected"));
        }

        String filename = file.getOriginalFilename();
        boolean validExtension = filename != null &&
                ALLOWED_EXTENSIONS.stream().anyMatch(ext -> filename.toLowerCase(Locale.ROOT).endsWith(ext));

        if (!validExtension) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Please upload an Excel file (.xlsx, .xls, or .xlsm)"
            ));
        }

        Map<String, List<String>> timesByRoute = new LinkedHashMap<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) { // skip header row
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String code = formatter.formatCellValue(row.getCell(0)).trim().toUpperCase(Locale.ROOT);
                // Column 1 (route_name) is intentionally not read here — it's
                // just for the admin's own readability in the sheet. The
                // authoritative name still comes from the Routes tab.
                String time = formatter.formatCellValue(row.getCell(2)).trim();

                if (code.isEmpty() || time.isEmpty()) continue;
                timesByRoute.computeIfAbsent(code, k -> new ArrayList<>()).add(time);
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to parse file: " + e.getMessage()));
        }

        if (timesByRoute.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "No valid rows found — expected columns: route_code, route_name, departure_time"
            ));
        }

        int updatedRoutes = 0;
        List<String> unknownCodes = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : timesByRoute.entrySet()) {
            Route route = routeRepository.findByRouteCode(entry.getKey()).orElse(null);
            if (route == null) {
                unknownCodes.add(entry.getKey());
                continue;
            }
            route.setDepartureTimes(entry.getValue());
            routeRepository.save(route);
            updatedRoutes++;
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Timetable updated for " + updatedRoutes + " route" + (updatedRoutes == 1 ? "" : "s"));
        response.put("routesUpdated", updatedRoutes);
        if (!unknownCodes.isEmpty()) {
            response.put("warning", "These route codes weren't found and were skipped: " + String.join(", ", unknownCodes));
        }
        return ResponseEntity.ok(response);
    }

    // Generates a ready-to-edit example .xlsx for the bulk departure-time upload above.
    @GetMapping("/departures/template")
    public ResponseEntity<byte[]> downloadDepartureTemplate() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Departure Times");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("route_code");
            header.createCell(1).setCellValue("route_name");
            header.createCell(2).setCellValue("departure_time");

            String[][] examples = {
                    {"APD_FLK", "Alipurduar -> Falakata", "06:00"},
                    {"APD_FLK", "Alipurduar -> Falakata", "09:30"},
                    {"APD_FLK", "Alipurduar -> Falakata", "14:15"},
                    {"APD_SLG", "Alipurduar -> Siliguri", "07:45"}
            };
            for (int i = 0; i < examples.length; i++) {
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(examples[i][0]);
                row.createCell(1).setCellValue(examples[i][1]);
                row.createCell(2).setCellValue(examples[i][2]);
            }

            sheet.setColumnWidth(0, 4000);
            sheet.setColumnWidth(1, 7000);
            sheet.setColumnWidth(2, 4000);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=departure-times-template.xlsx")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        }
    }

    // Manual, one-at-a-time counterpart to the bulk upload above — appends a
    // single departure time to an existing route (skips it if already
    // present) rather than replacing the whole list, since that's the more
    // intuitive behavior for adding one entry at a time from a form.
    @PostMapping("/departures/add")
    public ResponseEntity<?> addDepartureTime(
            @RequestParam("routeCode") String routeCode,
            @RequestParam("departureTime") String departureTime
    ) {
        String code = routeCode == null ? "" : routeCode.trim().toUpperCase(Locale.ROOT);
        String time = departureTime == null ? "" : departureTime.trim();

        if (code.isEmpty() || time.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Route code and departure time are both required"));
        }

        Route route = routeRepository.findByRouteCode(code).orElse(null);
        if (route == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Route not found: " + code));
        }

        List<String> times = new ArrayList<>(route.getDepartureTimes() == null ? List.of() : route.getDepartureTimes());
        if (!times.contains(time)) {
            times.add(time);
        }
        route.setDepartureTimes(times);
        routeRepository.save(route);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Added " + time + " to " + code);
        response.put("routeName", route.getRouteName());
        response.put("departureTimes", times);
        return ResponseEntity.ok(response);
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
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "busNumbers", required = false) String busNumbers
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
        // busNumbers is only touched when the field was actually submitted,
        // so callers that don't know about this feature yet (e.g. an old
        // cached admin page) can't accidentally wipe an existing roster.
        // Departure times aren't touched here at all — they're managed
        // exclusively from the Schedule tab now.
        if (busNumbers != null) {
            route.setBusNumbers(parseCommaList(busNumbers));
        }

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