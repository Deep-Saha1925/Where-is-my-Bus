/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

/**
 * Bus-number text handling shared by the passenger search. Kept separate from RideService
 * so the rule can be unit-tested on its own.
 */
public final class BusNumbers {

    /** Longest search text used (anything longer is cut, so absurd inputs can't reach the database). */
    public static final int MAX_QUERY_LENGTH = 30;

    private BusNumbers() {}

    /** Lower-case letters and digits only, so "WB-23A 1245" and "wb23a1245" compare equal. */
    public static String normalize(String value) {
        if (value == null) return "";
        String cleaned = value.toLowerCase().replaceAll("[^a-z0-9]", "");
        return cleaned.length() > MAX_QUERY_LENGTH ? cleaned.substring(0, MAX_QUERY_LENGTH) : cleaned;
    }
}
