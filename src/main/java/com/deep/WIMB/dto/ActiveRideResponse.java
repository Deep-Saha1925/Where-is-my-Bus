/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.dto;

import lombok.Data;

@Data
public class ActiveRideResponse {


    private Long rideId;
    private String busNumber;
    private String routeKey;
    private String routeCode;
    // Correctly-split ends of routeKey (stop names may contain "_")
    private String sourceName;
    private String destinationName;
    private Double latitude;
    private Double longitude;
    private Double remainingDistanceKm;
}