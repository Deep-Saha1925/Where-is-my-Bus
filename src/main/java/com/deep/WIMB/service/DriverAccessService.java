package com.deep.WIMB.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DriverAccessService {

    // LinkedHashMap keeps depots in the order they're listed in properties,
    // so the dropdown shows them in a sensible order rather than random.
    private final Map<String, String> depotCodes = new LinkedHashMap<>();

    public DriverAccessService(@Value("${wimb.depot.codes}") String rawCodes) {
        for (String entry : rawCodes.split(",")) {
            String[] parts = entry.trim().split(":");
            if (parts.length == 2) {
                depotCodes.put(parts[0].trim().toUpperCase(), parts[1].trim());
            }
        }
    }

    public boolean isValid(String depotName, String code) {
        if (depotName == null || code == null) return false;
        String expected = depotCodes.get(depotName.trim().toUpperCase());
        return expected != null && expected.equals(code.trim());
    }

    /** Depot names only — safe to expose publicly, no codes included. */
    public List<String> getDepotNames() {
        return new ArrayList<>(depotCodes.keySet());
    }
}