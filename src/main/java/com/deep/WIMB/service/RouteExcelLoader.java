package com.deep.WIMB.service;

import com.deep.WIMB.dto.RouteStop;
import jakarta.annotation.PostConstruct;
import org.apache.poi.ss.usermodel.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;

@Service
public class RouteExcelLoader {

    private final Map<String, List<RouteStop>> routeCache = new HashMap<>();

    public final String routeKey = "DEMO_ROUTES";

    @PostConstruct
    public void loadRoute() throws Exception {
        loadRoute(routeKey, "routes/route_SLG_NJP.xlsx");
    }

    private void loadRoute(String routeKey, String path) throws Exception {

        InputStream is = new ClassPathResource(path).getInputStream();
        Workbook wb = WorkbookFactory.create(is);
        Sheet sheet = wb.getSheetAt(0);

        List<RouteStop> stops = new ArrayList<>();

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {

            Row r = sheet.getRow(i);
            if (r == null) continue;

            // ✅ Validate ALL required cells
            if (isAnyCellMissing(r, 0, 1, 2, 3, 4, 5)) {
                System.out.println("Skipping row " + i + " due to missing cells");
                continue;
            }

            RouteStop stop = new RouteStop();
            stop.setStopOrder((int) r.getCell(0).getNumericCellValue());
            stop.setStopName(r.getCell(1).getStringCellValue().trim());
            stop.setLatitude(r.getCell(2).getNumericCellValue());
            stop.setLongitude(r.getCell(3).getNumericCellValue());
            stop.setDistanceFromStartKm(r.getCell(4).getNumericCellValue());
            stop.setSlackTimeMin((int) r.getCell(5).getNumericCellValue());

            stops.add(stop);
        }

        routeCache.put(routeKey, stops);
        wb.close();

        System.out.println("Loaded route " + routeKey + " with " + stops.size() + " stops");
    }

    // Utility method
    private boolean isAnyCellMissing(Row row, int... idxs) {
        for (int idx : idxs) {
            Cell c = row.getCell(idx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (c == null) return true;
        }
        return false;
    }

    public int getStopOrderByName(String stopName) {
        return routeCache.values()
                .stream()
                .flatMap(List::stream)
                .filter(s -> s.getStopName().equalsIgnoreCase(stopName))
                .findFirst()
                .orElseThrow(() ->
                        new RuntimeException("Stop not found: " + stopName))
                .getStopOrder();
    }


    public List<RouteStop> getFullRoute() {
        List<RouteStop> route = routeCache.get(routeKey);
        if (route == null) {
            throw new RuntimeException("Route not loaded: " + routeKey);
        }
        return route;
    }

    public List<RouteStop> getRouteBetween(
            String source,
            String destination
    ) {
        List<RouteStop> fullRoute = getFullRoute();

        int sourceIdx = -1;
        int destIdx = -1;

        for (int i = 0; i < fullRoute.size(); i++) {
            String stopName = fullRoute.get(i).getStopName();

            if (stopName.equalsIgnoreCase(source)) {
                sourceIdx = i;
            }
            if (stopName.equalsIgnoreCase(destination)) {
                destIdx = i;
            }
        }

        if (sourceIdx == -1 || destIdx == -1) {
            throw new RuntimeException("Invalid source or destination");
        }

        List<RouteStop> segment;

        // FORWARD
        if (sourceIdx < destIdx) {
            segment = new ArrayList<>(fullRoute.subList(sourceIdx, destIdx + 1));
        }

        // BACKWARD
        else{
            segment = new ArrayList<>(fullRoute.subList(destIdx, sourceIdx + 1));
            Collections.reverse(segment);
        }

        // Recalculate distance
        double baseDistance = segment.get(0).getDistanceFromStartKm();

        for (RouteStop stop : segment){
            double adjustedDistance = Math.abs(stop.getDistanceFromStartKm() - baseDistance);
            stop.setDistanceFromStartKm(adjustedDistance);
        }

        return segment;
    }
}
