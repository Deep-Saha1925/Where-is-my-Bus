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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/routes")
@RequiredArgsConstructor
public class AdminRouteController {

    private final RouteRepository routeRepository;
    private final RouteExcelLoader routeExcelLoader;

    @Value("${wimb.route.upload.dir}")
    private String routeUploadDir;

    private static final List<String> ALLOWED_EXTENSIONS = List.of(".xlsx", ".xls", ".xlsm");

    // Admin-only via the existing "/admin/**" -> hasRole("ADMIN") rule in SecurityConfig.
    @GetMapping
    public List<Map<String, Object>> listRoutes() {
        return routeRepository.findAll().stream()
                .map(route -> {
                    int stopCount = 0;
                    try {
                        stopCount = routeExcelLoader.getFullRoute(route.getRouteCode()).size();
                    } catch (Exception ignored) {
                        // Route exists in DB but isn't loaded in memory (e.g. file went missing) —
                        // surface it as 0 stops rather than failing the whole list.
                    }
                    return Map.of(
                            "routeCode", route.getRouteCode(),
                            "routeName", route.getRouteName(),
                            "stopCount", stopCount
                    );
                })
                .collect(Collectors.toList());
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadRoute(
            @RequestParam("routeCode") String routeCodeRaw,
            @RequestParam("routeName") String routeName,
            @RequestParam("file") MultipartFile file
    ) {
        String routeCode = routeCodeRaw == null ? "" : routeCodeRaw.trim().toUpperCase(Locale.ROOT);

        if (routeCode.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Route code is required"));
        }
        if (routeName == null || routeName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Route name is required"));
        }
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

        if (routeRepository.existsByRouteCode(routeCode)) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "A route with code \"" + routeCode + "\" already exists. " +
                            "Editing/replacing an existing route isn't supported yet — use a different code."
            ));
        }

        try {
            File dir = new File(routeUploadDir);
            dir.mkdirs();
            File dest = new File(dir, routeCode + ".xlsx");
            Files.write(dest.toPath(), file.getBytes());

            Route route = new Route();
            route.setRouteCode(routeCode);
            route.setRouteName(routeName.trim());
            route.setFilePath(dest.getPath());
            routeRepository.save(route);

            routeExcelLoader.loadRouteFromDisk(routeCode, dest.getPath());

            return ResponseEntity.ok(Map.of(
                    "message", "Route added successfully",
                    "routeCode", routeCode,
                    "stopCount", routeExcelLoader.getFullRoute(routeCode).size()
            ));
        } catch (Exception e) {
            // Roll back the DB record if the file wrote but parsing failed, so we don't end
            // up with a "registered" route that never actually loaded.
            routeRepository.findByRouteCode(routeCode).ifPresent(routeRepository::delete);
            return ResponseEntity.status(500).body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Route Stops");

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

            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, 4500);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=route-template.xlsx")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        }
    }
}