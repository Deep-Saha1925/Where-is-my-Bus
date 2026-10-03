/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.dto;

import com.deep.WIMB.model.Location;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Location payload shared by the WebSocket push (/topic/ride/{rideId}) and
 * the REST fallback (/api/location/last-loc/{rideId}), so track.js sees the
 * exact same shape from both.
 *
 * timestamp is an ISO-8601 *instant* ending in "Z" (e.g. 2026-09-29T08:30:00Z).
 * It used to be a zone-less LocalDateTime, which the browser reads as its own
 * local time -- for an IST user on a UTC server every update looked exactly
 * 5h30m old ("Updated 330m ago"). Over WebSocket it could even arrive as a
 * [y,m,d,h,...] array. An explicit instant is unambiguous everywhere.
 *
 * accuracy is the GPS accuracy radius in metres (null if unknown); track.js
 * uses it to size the "at a stop" radius around the bus.
 */
@Getter
@AllArgsConstructor
public class LocationBroadcast {
    private Long rideId;
    private double latitude;
    private double longitude;
    private Double accuracy;
    private String timestamp;

    public static LocationBroadcast of(Long rideId, Location loc, Double accuracy) {
        return new LocationBroadcast(
                rideId,
                loc.getLatitude(),
                loc.getLongitude(),
                accuracy,
                toIsoInstant(loc.getTimestamp())
        );
    }

    /** Interprets a server-local LocalDateTime in the server's zone and renders it as a UTC instant. */
    public static String toIsoInstant(LocalDateTime t) {
        if (t == null) return null;
        return t.atZone(ZoneId.systemDefault()).toInstant().toString();
    }
}