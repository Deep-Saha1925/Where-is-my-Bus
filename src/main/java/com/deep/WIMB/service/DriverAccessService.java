package com.deep.WIMB.service;

import com.deep.WIMB.model.AdminUpload;
import com.deep.WIMB.repository.AdminUploadRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Holds the depot-code list drivers enter to start a ride. The file backing
 * this used to live only on local disk, which meant every redeploy silently
 * wiped it — the exact same class of bug that route files and departure
 * timetables had before those were moved into Postgres. This does the same
 * for depot codes: the file's bytes live in the admin_upload table (kind =
 * "depot-codes"), so a redeploy or container restart can't lose them.
 */
@Service
@RequiredArgsConstructor
public class DriverAccessService {

    private final AdminUploadRepository adminUploadRepository;

    // Kept only as a one-time migration path for whatever might already be
    // sitting on disk from before this change — see loadFromDiskIfPresent().
    // Nothing in normal operation writes here anymore.
    @Value("${wimb.depot.file.path}")
    private String legacyDepotFilePath;

    private volatile Map<String, String> depotCodes = new LinkedHashMap<>();

    @jakarta.annotation.PostConstruct
    public void init() {
        reload();
    }

    /** Loads the depot list from the database. Safe to call again later
     *  (the admin panel calls this right after a new file is uploaded). */
    public synchronized void reload() {
        AdminUpload stored = adminUploadRepository.findByKind(AdminUpload.KIND_DEPOT_CODES).orElse(null);

        if (stored == null || stored.getFileData() == null) {
            byte[] migrated = loadFromDiskIfPresent();
            if (migrated != null) {
                storeBytes(migrated, "depot-codes.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                reload(); // now present in the DB — load it the normal way
                return;
            }

            System.out.println("No depot codes uploaded yet — waiting for admin to upload a file.");
            depotCodes = new LinkedHashMap<>();
            return;
        }

        depotCodes = parse(stored.getFileData());
        System.out.println("Loaded " + depotCodes.size() + " depot codes from the database (\""
                + stored.getFileName() + "\", uploaded " + stored.getUploadedAt() + ")");
    }

    /** Called by the admin upload endpoint with a freshly uploaded file's
     *  bytes: validates it parses, then persists and switches to it. */
    public synchronized void replace(byte[] bytes, String fileName, String contentType) {
        Map<String, String> parsed = parse(bytes); // throws if the file is unreadable — fail before saving
        storeBytes(bytes, fileName, contentType);
        depotCodes = parsed;
        System.out.println("Loaded " + parsed.size() + " depot codes from newly uploaded \"" + fileName + "\"");
    }

    private void storeBytes(byte[] bytes, String fileName, String contentType) {
        AdminUpload record = adminUploadRepository.findByKind(AdminUpload.KIND_DEPOT_CODES).orElseGet(AdminUpload::new);
        record.setKind(AdminUpload.KIND_DEPOT_CODES);
        record.setFileName(fileName == null || fileName.isBlank() ? "depot-codes.xlsx" : fileName);
        record.setContentType(contentType);
        record.setFileData(bytes);
        record.setUploadedAt(LocalDateTime.now());
        adminUploadRepository.save(record);
    }

    /** One-time recovery for a container that still has the old disk file
     *  from before this migration — read it once so it can be copied into
     *  the DB and never depended on again. Returns null if there's nothing
     *  there, which is the expected, permanent state going forward. */
    private byte[] loadFromDiskIfPresent() {
        try {
            File file = new File(legacyDepotFilePath);
            if (!file.exists()) return null;
            byte[] bytes = Files.readAllBytes(file.toPath());
            System.out.println("Found a legacy depot-codes file on local disk at " + legacyDepotFilePath
                    + " — migrating it into the database so it's no longer lost on redeploy.");
            return bytes;
        } catch (Exception e) {
            System.err.println("Could not read legacy depot-codes file from disk (non-fatal): " + e.getMessage());
            return null;
        }
    }

    private Map<String, String> parse(byte[] bytes) {
        Map<String, String> parsed = new LinkedHashMap<>();
        DataFormatter formatter = new DataFormatter();

        // WorkbookFactory reads the file's actual signature/bytes, so it
        // transparently handles .xlsx, .xls (old binary format), and .xlsm
        // regardless of what extension the admin's file happened to have.
        try (ByteArrayInputStream is = new ByteArrayInputStream(bytes);
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) { // skip header row
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String depotName = formatter.formatCellValue(row.getCell(0)).trim();
                String code      = formatter.formatCellValue(row.getCell(1)).trim();

                if (!depotName.isEmpty() && !code.isEmpty()) {
                    parsed.put(depotName.toUpperCase(), code);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse depot codes file: " + e.getMessage(), e);
        }

        return parsed;
    }

    public boolean isValid(String depotName, String code) {
        if (depotName == null || code == null) return false;
        String expected = depotCodes.get(depotName.trim().toUpperCase());
        return expected != null && expected.equals(code.trim());
    }

    public List<String> getDepotNames() {
        return new ArrayList<>(depotCodes.keySet());
    }

    public Map<String, String> getAllDepotCodes() {
        return new LinkedHashMap<>(depotCodes);
    }

    /** Metadata about the currently stored file, for the admin panel's
     *  "last uploaded" panel — mirrors the same idea already used for
     *  routes and departure timetables. */
    public AdminUpload getStoredFileMetadata() {
        return adminUploadRepository.findByKind(AdminUpload.KIND_DEPOT_CODES).orElse(null);
    }
}