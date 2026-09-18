package com.deep.WIMB.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * One route that connects two depots — used by the static "search buses
 * between depots" passenger feature. This is deliberately NOT live/GPS
 * data: it answers "does a service exist here" from admin-registered
 * route + bus-roster info, regardless of whether anything is on the road
 * right now.
 */
@Getter
@AllArgsConstructor
public class DepotRouteMatch {
    private String routeCode;
    private String routeName;
    private String sourceDepot;
    private String destinationDepot;
    private int stopsBetween;      // inclusive count of stops from source to destination
    private double distanceKm;
    private List<String> busNumbers; // may be empty if admin hasn't registered any yet
    private List<String> departureTimes; // e.g. ["06:00", "09:30"] — may be empty if no schedule registered yet
}