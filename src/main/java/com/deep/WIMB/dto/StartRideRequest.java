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