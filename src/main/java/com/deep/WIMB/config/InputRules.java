/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.config;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

/**
 * Central input checks for the public driver endpoints. Bus numbers are typed by
 * drivers and later shown to every passenger and to the admin, so anything that
 * isn't a plain bus-number shape (e.g. HTML like "&lt;img onerror=...&gt;") is
 * rejected before it can be stored.
 */
public final class InputRules {

    private InputRules() {}

    // letters, digits, space and hyphen; 3-20 chars  (WB-23A-1245, WB 23A 1245 ...)
    private static final Pattern BUS_NUMBER = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 \\-]{2,19}$");
    private static final Pattern ROUTE_CODE = Pattern.compile("^[A-Za-z0-9_\\-]{1,50}$");

    public static String busNumber(String raw) {
        String v = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (!BUS_NUMBER.matcher(v).matches()) {
            throw bad("Invalid bus number. Use only letters, digits, spaces and hyphens (3-20 characters).");
        }
        return v.toUpperCase();
    }

    /** Blank/null is allowed (legacy route); otherwise a short code like APD_FLK. */
    public static String routeCodeOrNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim();
        if (!ROUTE_CODE.matcher(v).matches()) throw bad("Invalid route code");
        return v;
    }

    /** Free text such as a routeKey: bounded length, no control chars, no HTML characters (apostrophes are fine: stop names like "St Mary's"). */
    public static String safeText(String raw, String field, int maxLength) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.length() > maxLength || v.chars().anyMatch(c -> c < 32 || c == '<' || c == '>' || c == '"' || c == '`')) {
            throw bad("Invalid " + field);
        }
        return v;
    }

    public static void coordinates(double lat, double lng) {
        if (!Double.isFinite(lat) || !Double.isFinite(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw bad("Invalid coordinates");
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
