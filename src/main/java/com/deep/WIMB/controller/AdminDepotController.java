package com.deep.WIMB.controller;

import com.deep.WIMB.service.DriverAccessService;
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

@RestController
@RequestMapping("/admin/depots")
@RequiredArgsConstructor
public class AdminDepotController {

    private final DriverAccessService driverAccessService;

    @Value("${wimb.depot.file.path}")
    private String depotFilePath;

    private static final List<String> ALLOWED_EXTENSIONS = List.of(".xlsx", ".xls", ".xlsm");

    @GetMapping
    public Map<String, String> viewCurrentDepots() {
        return driverAccessService.getAllDepotCodes();
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadDepotFile(@RequestParam("file") MultipartFile file) {
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

        try {
            File dest = new File(depotFilePath);
            dest.getParentFile().mkdirs();
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

    // Generates a ready-to-edit example .xlsx for the admin to download,
    // fill in with real depots, and re-upload.
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Depot Codes");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("depot_name");
            header.createCell(1).setCellValue("code");

            String[][] examples = {
                    {"ALIPURDUAR", "101"},
                    {"SILIGURI", "201"},
                    {"COOCHBEHAR", "301"}
            };
            for (int i = 0; i < examples.length; i++) {
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(examples[i][0]);
                row.createCell(1).setCellValue(examples[i][1]);
            }

            sheet.setColumnWidth(0, 5000);
            sheet.setColumnWidth(1, 3000);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=depot-codes-template.xlsx")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        }
    }
}