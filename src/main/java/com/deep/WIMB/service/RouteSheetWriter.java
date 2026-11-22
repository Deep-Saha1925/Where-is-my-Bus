/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import com.deep.WIMB.dto.RouteStop;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.util.List;

/** Writes a list of stops as the standard route sheet (same columns as the downloadable template). */
public final class RouteSheetWriter {

    private RouteSheetWriter() {}

    public static byte[] write(List<RouteStop> stops) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Route");
            String[] headers = {"stop_order", "stop_name", "latitude", "longitude", "distance_km", "slack_min"};
            Row h = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) h.createCell(i).setCellValue(headers[i]);
            for (int i = 0; i < stops.size(); i++) {
                RouteStop s = stops.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(s.getStopOrder());
                row.createCell(1).setCellValue(s.getStopName());
                row.createCell(2).setCellValue(s.getLatitude());
                row.createCell(3).setCellValue(s.getLongitude());
                row.createCell(4).setCellValue(s.getDistanceFromStartKm());
                row.createCell(5).setCellValue(s.getSlackTimeMin());
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }
}
