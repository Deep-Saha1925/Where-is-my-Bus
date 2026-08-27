package com.deep.WIMB.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RouteSummary {
    private String routeCode;
    private String routeName;
    private int stopCount;
}