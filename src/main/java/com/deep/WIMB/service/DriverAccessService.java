package com.deep.WIMB.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class DriverAccessService {

    private final Map<String, String> busCodes = new HashMap<>();

    public DriverAccessService(@Value("${wimb.driver.codes}") String rawCodes) {
        for (String entry : rawCodes.split(",")) {
            String[] parts = entry.trim().split(":");
            if (parts.length == 2) {
                busCodes.put(parts[0].trim().toUpperCase(), parts[1].trim());
            }
        }
    }

    public boolean isValid(String busNumber, String code) {
        if (busNumber == null || code == null) return false;
        String expected = busCodes.get(busNumber.trim().toUpperCase());
        return expected != null && expected.equals(code.trim());
    }
}