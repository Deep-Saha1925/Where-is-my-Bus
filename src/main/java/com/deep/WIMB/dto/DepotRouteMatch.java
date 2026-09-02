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
