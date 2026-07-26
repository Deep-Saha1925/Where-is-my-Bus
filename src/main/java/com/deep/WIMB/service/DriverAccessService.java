package com.deep.WIMB.service;

import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

@Service
public class DriverAccessService {

    @Value("${wimb.depot.file.path}")
    private String depotFilePath;

    private volatile Map<String, String> depotCodes = new LinkedHashMap<>();

    /** Called once at startup, and again every time admin uploads a new file. */
    public synchronized void reload() {
        File file = new File(depotFilePath);

        if (!file.exists()) {
            System.out.println("No depot-codes file found at " + depotFilePath +
                    " yet — waiting for admin to upload one.");
            depotCodes = new LinkedHashMap<>();
            return;
        }

        Map<String, String> parsed = new LinkedHashMap<>();
        DataFormatter formatter = new DataFormatter();

        // WorkbookFactory reads the file's actual signature/bytes, so it
        // transparently handles .xlsx, .xls (old binary format), and .xlsm
        // regardless of what extension the admin's file happened to have.
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {

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

            depotCodes = parsed;
            System.out.println("Loaded " + parsed.size() + " depot codes from " + depotFilePath);

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse depot codes file: " + e.getMessage(), e);
        }
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        reload();
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
}