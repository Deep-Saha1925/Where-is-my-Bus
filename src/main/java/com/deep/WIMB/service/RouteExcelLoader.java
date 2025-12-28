    package com.deep.WIMB.service;
    
    import com.deep.WIMB.dto.RouteStop;
    import jakarta.annotation.PostConstruct;
    import org.apache.poi.ss.usermodel.Row;
    import org.apache.poi.ss.usermodel.Sheet;
    import org.apache.poi.ss.usermodel.Workbook;
    import org.apache.poi.ss.usermodel.WorkbookFactory;
    import org.springframework.core.io.ClassPathResource;
    import org.springframework.stereotype.Service;
    
    import java.io.InputStream;
    import java.util.ArrayList;
    import java.util.HashMap;
    import java.util.List;
    import java.util.Map;

    @Service
    public class RouteExcelLoader {

        private final Map<String, List<RouteStop>> routeCache = new HashMap<>();

        @PostConstruct
        public void loadRoute() throws Exception {
            loadRoute("SLG_NJP", "routes/route_SLG_NJP.xlsx");
        }

        private void loadRoute(String routeKey, String path) throws Exception {
            InputStream is = new ClassPathResource(path).getInputStream();
            Workbook wb = WorkbookFactory.create(is);
            Sheet sheet = wb.getSheetAt(0);

            List<RouteStop> stops = new ArrayList<>();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row r = sheet.getRow(i);
                if (r == null) continue;

                RouteStop stop = new RouteStop();
                stop.setStopOrder((int) r.getCell(0).getNumericCellValue());
                stop.setStopName(r.getCell(1).getStringCellValue());
                stop.setLatitude(r.getCell(2).getNumericCellValue());
                stop.setLongitude(r.getCell(3).getNumericCellValue());
                stop.setDistanceFromStartKm(r.getCell(4).getNumericCellValue());
                stop.setSlackTimeMin((int) r.getCell(5).getNumericCellValue());

                stops.add(stop);
            }

            routeCache.put(routeKey, stops);
            wb.close();
        }

        public List<RouteStop> getRoute(String routeKey) {
            return routeCache.get(routeKey);
        }
    }