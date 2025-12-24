package com.deep.WIMB.dto;

import lombok.Data;

@Data
public class ActiveRideResponse {

    private Long rideId;
    private String busNumber;
    private String source;
    private String destination;
    private Double latitude;
    private Double longitude;

}
