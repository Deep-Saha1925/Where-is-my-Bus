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

    @PostMapping("/upload")
    public ResponseEntity<?> uploadRoute(
            @RequestParam("routeCode") String routeCode,
            @RequestParam("routeName") String routeName,
            @RequestParam("file") MultipartFile file
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
            File dest = new File(routeDir, code + extension);
            dest.getParentFile().mkdirs();
            Files.write(dest.toPath(), file.getBytes());

            Route route = new Route();
            route.setRouteCode(code);
            route.setRouteName(name);
            route.setFilePath(dest.getPath());
            route.setUploadedAt(LocalDateTime.now());

            // Parse before committing to the DB — a malformed file fails loudly here
            // instead of registering a route that can never actually be loaded.
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

    // Ready-to-edit example file, same shape as your existing route Excel files.
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
}