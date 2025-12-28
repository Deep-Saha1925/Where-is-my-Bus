package com.deep.WIMB.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LocationUpdateRequest {
    private Long rideId;
    private double latitude;
    private double longitude;
}
