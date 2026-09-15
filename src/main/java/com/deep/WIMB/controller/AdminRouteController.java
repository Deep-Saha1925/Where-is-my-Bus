package com.deep.WIMB.controller;

import com.deep.WIMB.model.AdminUpload;
import com.deep.WIMB.model.Route;
import com.deep.WIMB.repository.AdminUploadRepository;
import com.deep.WIMB.repository.RouteRepository;
import com.deep.WIMB.service.RouteExcelLoader;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/admin/routes")
@RequiredArgsConstructor
public class AdminRouteController {

    private final RouteRepository routeRepository;
    private final RouteExcelLoader routeExcelLoader;
    private final AdminUploadRepository adminUploadRepository;

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

    // ── Shared spreadsheet-parsing helpers ───────────────────────────────

    /** Excel cells routinely carry invisible junk that trim() misses — most
     *  commonly a non-breaking space (U+00A0) from a copy-paste out of a web
     *  page or PDF. A route code of "APD_FLK\u00A0" looks identical on screen
     *  but never matches anything in the database, which is exactly the kind
     *  of silent mismatch that makes an upload report zero rows. */
    private String cleanCell(String raw) {
        if (raw == null) return "";
        return raw.replace('\u00A0', ' ')
                .replace('\u200B', ' ')
                .replace('\uFEFF', ' ')
                .trim();
    }

    /** Reads a departure time out of a cell in whatever form Excel chose to
     *  store it. Typing "06:00" into Excel does NOT give you the string
     *  "06:00" — Excel silently converts it to the number 0.25 with a time
     *  format attached. Reading that as plain text is what produced garbage
     *  (or empty) departure times, so date/time-formatted numeric cells are
     *  converted back to a real HH:mm string here. */
    private String readTimeCell(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";

        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType()
                : cell.getCellType();

        if (type == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (DateUtil.isCellDateFormatted(cell)) {
                LocalTime time = DateUtil.getLocalDateTime(value).toLocalTime();
                return time.format(DateTimeFormatter.ofPattern("HH:mm"));
            }
            // A bare fraction between 0 and 1 in a "departure time" column is
            // almost certainly a time that lost its formatting somewhere.
            if (value > 0 && value < 1) {
                int minutesOfDay = (int) Math.round(value * 24 * 60);
                return String.format("%02d:%02d", (minutesOfDay / 60) % 24, minutesOfDay % 60);
            }
        }

        return cleanCell(formatter.formatCellValue(cell));
    }

    /** Builds a header-name to column-index map from row 0, so column ORDER
     *  and column COUNT stop mattering. The previous version hard-coded
     *  getCell(0) and getCell(2); any sheet without a middle route_name
     *  column (a perfectly reasonable two-column code + time sheet) had every
     *  single row skipped, which is the "no valid rows" failure. */
    private Map<String, Integer> readHeader(Row header, DataFormatter formatter) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        if (header == null) return columns;

        for (int c = header.getFirstCellNum(); c < header.getLastCellNum(); c++) {
            String label = cleanCell(formatter.formatCellValue(header.getCell(c)))
                    .toLowerCase(Locale.ROOT)
                    .replace(' ', '_');
            if (!label.isEmpty()) columns.putIfAbsent(label, c);
        }
        return columns;
    }

    /** Finds a column whose header contains all the given fragments. */
    private int findColumn(Map<String, Integer> columns, String... fragments) {
        for (Map.Entry<String, Integer> entry : columns.entrySet()) {
            boolean matchesAll = true;
            for (String fragment : fragments) {
                if (!entry.getKey().contains(fragment)) { matchesAll = false; break; }
            }
            if (matchesAll) return entry.getValue();
        }
        return -1;
    }

    // Bulk alternative to the manual add-one-row form below: upload one
    // sheet covering many routes' timetables at once. Columns are located by
    // HEADER NAME rather than by position, so route_code / route_name /
    // departure_time can appear in any order, and route_name can be omitted
    // entirely. "mode" chooses between replacing a route's timetable outright
    // and merging the file's times into what's already there.
    @PostMapping("/departures/upload")
    public ResponseEntity<?> uploadDepartureTimes(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", required = false, defaultValue = "replace") String mode
    ) {
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

        boolean merge = "merge".equalsIgnoreCase(mode);

        Map<String, List<String>> timesByRoute = new LinkedHashMap<>();
        int rowsSeen = 0;
        int rowsSkipped = 0;
        byte[] fileBytes;

        try {
            fileBytes = file.getBytes();
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Could not read the uploaded file: " + e.getMessage()));
        }

        try (InputStream is = new java.io.ByteArrayInputStream(fileBytes);
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            Map<String, Integer> columns = readHeader(headerRow, formatter);

            int codeIdx = findColumn(columns, "route", "code");
            if (codeIdx < 0) codeIdx = findColumn(columns, "code");
            int timeIdx = findColumn(columns, "depart");
            if (timeIdx < 0) timeIdx = findColumn(columns, "time");

            // If the header row wasn't recognisable, fall back to position —
            // and in that case DON'T skip the first row, because it's data,
            // not a header. A two-column sheet puts the time in column 1;
            // three or more columns put it in column 2 (route_name in between).
            boolean headerRecognised = codeIdx >= 0 && timeIdx >= 0;
            int firstDataRow = sheet.getFirstRowNum() + (headerRecognised ? 1 : 0);
            if (!headerRecognised) {
                codeIdx = 0;
                int lastCol = headerRow == null ? 2 : Math.max(1, headerRow.getLastCellNum() - 1);
                timeIdx = lastCol >= 2 ? 2 : 1;
            }

            for (int i = firstDataRow; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String code = cleanCell(formatter.formatCellValue(row.getCell(codeIdx))).toUpperCase(Locale.ROOT);
                String time = readTimeCell(row.getCell(timeIdx), formatter);

                if (code.isEmpty() && time.isEmpty()) continue; // genuinely blank spacer row

                rowsSeen++;

                // Guard against a header row that slipped through the
                // positional fallback above being stored as real data.
                if (code.equalsIgnoreCase("ROUTE_CODE") || time.equalsIgnoreCase("departure_time")) {
                    continue;
                }
                if (code.isEmpty() || time.isEmpty()) {
                    rowsSkipped++;
                    continue;
                }

                timesByRoute.computeIfAbsent(code, k -> new ArrayList<>()).add(time);
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to parse file: " + e.getMessage()));
        }

        if (timesByRoute.isEmpty()) {
            String detail = rowsSeen == 0
                    ? "The sheet has no data rows below the header."
                    : "Read " + rowsSeen + " row(s), but none had both a route code and a departure time.";
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "No usable rows found. " + detail
                            + " Expected columns: route_code, route_name (optional), departure_time."
            ));
        }

        int updatedRoutes = 0;
        int timesWritten = 0;
        List<String> unknownCodes = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : timesByRoute.entrySet()) {
            Route route = routeRepository.findByRouteCode(entry.getKey()).orElse(null);
            if (route == null) {
                unknownCodes.add(entry.getKey());
                continue;
            }

            // De-duplicate within the file itself, and (in merge mode) against
            // what the route already has, so re-uploading the same sheet twice
            // can never double up a route's timetable.
            LinkedHashSet<String> times = new LinkedHashSet<>();
            if (merge && route.getDepartureTimes() != null) {
                times.addAll(route.getDepartureTimes());
            }
            times.addAll(entry.getValue());

            route.setDepartureTimes(new ArrayList<>(times));
            routeRepository.save(route);
            updatedRoutes++;
            timesWritten += entry.getValue().size();
        }

        // Nothing matched: this used to return 200 with "updated for 0 routes",
        // which the admin page rendered as a green success tick even though
        // absolutely nothing had been saved. It's an error, so say so.
        if (updatedRoutes == 0) {
            List<String> known = new ArrayList<>();
            for (Route r : routeRepository.findAllByOrderByRouteNameAsc()) known.add(r.getRouteCode());

            return ResponseEntity.badRequest().body(Map.of(
                    "error", "None of the route codes in the file match a registered route, so nothing was saved. "
                            + "File contained: " + String.join(", ", unknownCodes) + ". "
                            + (known.isEmpty()
                            ? "No routes are registered yet — add one in the Routes tab first."
                            : "Registered route codes are: " + String.join(", ", known) + ".")
            ));
        }

        // Keep the file itself so the admin can download exactly what was last
        // uploaded, rather than only the blank template.
        String summary = "Updated " + updatedRoutes + " route" + (updatedRoutes == 1 ? "" : "s")
                + " (" + timesWritten + " departure time" + (timesWritten == 1 ? "" : "s") + ")"
                + (unknownCodes.isEmpty() ? "" : ", " + unknownCodes.size() + " unknown code(s) skipped");
        storeUpload(AdminUpload.KIND_DEPARTURES, filename, file.getContentType(), fileBytes, rowsSeen - rowsSkipped, summary);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", (merge ? "Merged into " : "Replaced timetable for ")
                + updatedRoutes + " route" + (updatedRoutes == 1 ? "" : "s")
                + " (" + timesWritten + " departure time" + (timesWritten == 1 ? "" : "s") + ")");
        response.put("routesUpdated", updatedRoutes);
        response.put("timesWritten", timesWritten);
        response.put("rowsRead", rowsSeen);
        if (rowsSkipped > 0) {
            response.put("warning", rowsSkipped + " row(s) were incomplete and skipped");
        }
        if (!unknownCodes.isEmpty()) {
            response.put("warning", "These route codes weren't found and were skipped: " + String.join(", ", unknownCodes));
        }
        return ResponseEntity.ok(response);
    }

    /** Overwrites the single stored file for this kind. */
    private void storeUpload(String kind, String fileName, String contentType,
                             byte[] bytes, int rowCount, String summary) {
        try {
            AdminUpload stored = adminUploadRepository.findByKind(kind).orElseGet(AdminUpload::new);
            stored.setKind(kind);
            stored.setFileName(fileName == null || fileName.isBlank() ? kind + ".xlsx" : fileName);
            stored.setContentType(contentType);
            stored.setFileData(bytes);
            stored.setUploadedAt(LocalDateTime.now());
            stored.setRowCount(rowCount);
            stored.setSummary(summary);
            adminUploadRepository.save(stored);
        } catch (Exception e) {
            // Archiving the file is a convenience, never a reason to fail an
            // upload whose actual timetable changes already committed fine.
            System.err.println("Note: could not archive uploaded file (non-fatal): " + e.getMessage());
        }
    }

    /** Metadata about the most recently uploaded timetable file, for the
     *  "last upload" panel in the Schedule tab. Returns exists:false rather
     *  than a 404 so the page can render an empty state without an error. */
    @GetMapping("/departures/latest")
    public ResponseEntity<?> latestDepartureUpload() {
        AdminUpload stored = adminUploadRepository.findByKind(AdminUpload.KIND_DEPARTURES).orElse(null);

        Map<String, Object> response = new LinkedHashMap<>();
        if (stored == null || stored.getFileData() == null) {
            response.put("exists", false);
            return ResponseEntity.ok(response);
        }

        response.put("exists", true);
        response.put("fileName", stored.getFileName());
        response.put("uploadedAt", stored.getUploadedAt());
        response.put("rowCount", stored.getRowCount());
        response.put("sizeBytes", stored.getFileData().length);
        response.put("summary", stored.getSummary());
        return ResponseEntity.ok(response);
    }

    /** Downloads the exact bytes of the last uploaded timetable file. */
    @GetMapping("/departures/latest/file")
    public ResponseEntity<?> downloadLatestDepartureUpload() {
        AdminUpload stored = adminUploadRepository.findByKind(AdminUpload.KIND_DEPARTURES).orElse(null);

        if (stored == null || stored.getFileData() == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "No timetable file has been uploaded yet"));
        }

        String contentType = stored.getContentType() == null || stored.getContentType().isBlank()
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : stored.getContentType();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + stored.getFileName().replace("\"", "") + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(stored.getFileData());
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

    /** Normalises a hand-typed departure time to HH:mm where possible, so
     *  "6:00", "06:00" and "6:00 AM" don't end up stored as three separate
     *  entries for the same departure. Anything that isn't recognisably a
     *  time is kept verbatim — this field is deliberately free-text. */
    private String normalizeTimeText(String raw) {
        String text = cleanCell(raw);
        if (text.isEmpty()) return text;

        String[] patterns = {"H:mm", "HH:mm", "h:mm a", "hh:mm a", "H.mm", "HH.mm", "HHmm"};
        for (String pattern : patterns) {
            try {
                LocalTime time = LocalTime.parse(text.toUpperCase(Locale.ROOT),
                        DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
                return time.format(DateTimeFormatter.ofPattern("HH:mm"));
            } catch (Exception ignored) {
                // try the next pattern
            }
        }
        return text;
    }

    // Manual, one-at-a-time counterpart to the bulk upload above — appends a
    // single departure time to an existing route rather than replacing the
    // whole list, since that's the more intuitive behavior for adding one
    // entry at a time from a form. A duplicate is reported back explicitly
    // instead of being silently swallowed.
    @PostMapping("/departures/add")
    public ResponseEntity<?> addDepartureTime(
            @RequestParam("routeCode") String routeCode,
            @RequestParam("departureTime") String departureTime
    ) {
        String code = cleanCell(routeCode).toUpperCase(Locale.ROOT);
        String time = normalizeTimeText(departureTime);

        if (code.isEmpty() || time.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Route code and departure time are both required"));
        }

        Route route = routeRepository.findByRouteCode(code).orElse(null);
        if (route == null) {
            List<String> known = new ArrayList<>();
            for (Route r : routeRepository.findAllByOrderByRouteNameAsc()) known.add(r.getRouteCode());
            return ResponseEntity.status(404).body(Map.of("error",
                    "Route not found: " + code
                            + (known.isEmpty() ? " — no routes are registered yet."
                            : " — registered codes are: " + String.join(", ", known))));
        }

        List<String> times = new ArrayList<>(route.getDepartureTimes() == null ? List.of() : route.getDepartureTimes());
        boolean alreadyPresent = times.contains(time);
        if (!alreadyPresent) {
            times.add(time);
            times.sort(String::compareTo); // keeps the timetable in chronological order
            route.setDepartureTimes(times);
            routeRepository.save(route);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", alreadyPresent
                ? time + " is already on " + code + " — nothing to add"
                : "Added " + time + " to " + code);
        response.put("added", !alreadyPresent);
        response.put("routeName", route.getRouteName());
        response.put("departureTimes", times);
        return ResponseEntity.ok(response);
    }

    /** Replaces one route's whole timetable — the Schedule tab's row-level
     *  "Update" button, mirroring how the Routes tab edits a route inline. */
    @PutMapping("/{routeCode}/departures")
    public ResponseEntity<?> updateDepartureTimes(
            @PathVariable String routeCode,
            @RequestParam(value = "departureTimes", required = false) String departureTimes
    ) {
        String code = cleanCell(routeCode).toUpperCase(Locale.ROOT);

        Route route = routeRepository.findByRouteCode(code).orElse(null);
        if (route == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Route not found: " + code));
        }

        LinkedHashSet<String> times = new LinkedHashSet<>();
        for (String piece : parseCommaList(departureTimes)) {
            String normalized = normalizeTimeText(piece);
            if (!normalized.isEmpty()) times.add(normalized);
        }

        List<String> ordered = new ArrayList<>(times);
        ordered.sort(String::compareTo);
        route.setDepartureTimes(ordered);
        routeRepository.save(route);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", ordered.isEmpty()
                ? "Timetable cleared for \"" + code + "\""
                : "Timetable for \"" + code + "\" updated (" + ordered.size() + " departure"
                  + (ordered.size() == 1 ? "" : "s") + ")");
        response.put("departureTimes", ordered);
        return ResponseEntity.ok(response);
    }

    /** Clears one route's timetable — the Schedule tab's row-level "Delete".
     *  This only removes the departure times; the route itself, its stops and
     *  its bus roster are all left intact (deleting the route itself is still
     *  done from the Routes tab). */
    @DeleteMapping("/{routeCode}/departures")
    public ResponseEntity<?> deleteDepartureTimes(@PathVariable String routeCode) {
        String code = cleanCell(routeCode).toUpperCase(Locale.ROOT);

        Route route = routeRepository.findByRouteCode(code).orElse(null);
        if (route == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Route not found: " + code));
        }

        int removed = route.getDepartureTimes() == null ? 0 : route.getDepartureTimes().size();
        route.setDepartureTimes(new ArrayList<>());
        routeRepository.save(route);

        return ResponseEntity.ok(Map.of(
                "message", "Removed " + removed + " departure time" + (removed == 1 ? "" : "s") + " from \"" + code + "\"",
                "removed", removed
        ));
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