/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StartRideRequest {

    private String busNumber;
    private String routeKey;
    private String routeCode; // which registered route the driver picked; null/blank = legacy route
    private double latitude;
    private double longitude;
}