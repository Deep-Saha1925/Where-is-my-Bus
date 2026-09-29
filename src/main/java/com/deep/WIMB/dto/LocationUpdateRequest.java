package com.deep.WIMB.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LocationUpdateRequest {
    private Long rideId;
    private double latitude;
    private double longitude;
    // GPS accuracy radius in metres, as reported by the driver's device.
    // Optional -- older clients don't send it.
    private Double accuracy;
}