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