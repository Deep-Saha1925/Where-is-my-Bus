package com.deep.WIMB.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class RouteStop {

    private int stopOrder;
    private String stopName;
    private double latitude;
    private double longitude;
    private double distanceFromStartKm;
    private int slackTimeMin;

}
