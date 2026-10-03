/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import com.deep.WIMB.dto.ActiveRideResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived cache for passenger "search by bus number" results, so a burst of identical
 * searches (several passengers typing the same number, or one passenger retyping it) hits
 * the database once per few seconds instead of once per keystroke.
 *
 * Deliberately tiny and dependency-free: a ConcurrentHashMap with a time-to-live. Entries
 * expire on their own after TTL_MS, and the whole cache is cleared whenever a ride starts
 * or ends (see RideService#broadcastActiveRides), so a bus that has just started or
 * stopped never shows up stale. Single-instance only, like ActiveRideCache.
 */
@Component
public class BusSearchCache {

    private static final long TTL_MS = 5_000;
    private static final int  MAX_ENTRIES = 500; // guards against memory growth from random queries

    private record Entry(List<ActiveRideResponse> value, long expiresAt) {}

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    /** Cached results for this normalised query, or null if missing / expired. */
    public List<ActiveRideResponse> get(String query) {
        Entry e = entries.get(query);
        if (e == null) return null;
        if (e.expiresAt() < System.currentTimeMillis()) {
            entries.remove(query, e);
            return null;
        }
        return e.value();
    }

    public void put(String query, List<ActiveRideResponse> value) {
        if (entries.size() >= MAX_ENTRIES) {
            entries.clear();
        }
        entries.put(query, new Entry(List.copyOf(value), System.currentTimeMillis() + TTL_MS));
    }

    public void clear() {
        entries.clear();
    }
}
