package com.deep.WIMB.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RouteStop {

    private int stopOrder;
    private String stopName;
    private double latitude;
    private double longitude;
    private double distanceFromStartKm;
    private int slackTimeMin;

}
