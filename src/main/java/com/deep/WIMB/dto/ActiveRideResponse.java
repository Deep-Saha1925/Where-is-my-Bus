package com.deep.WIMB.dto;

import lombok.Data;

@Data
public class ActiveRideResponse {


    private Long rideId;
    private String busNumber;
    private String routeKey;
    private String routeCode;
    private Double latitude;
    private Double longitude;
    private Double remainingDistanceKm;
}