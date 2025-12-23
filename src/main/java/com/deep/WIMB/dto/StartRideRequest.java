package com.deep.WIMB.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StartRideRequest {

    private String busNumber;
    private String source;
    private String destination;
}
