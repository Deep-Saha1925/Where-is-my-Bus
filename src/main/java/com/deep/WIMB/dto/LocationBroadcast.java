package com.deep.WIMB.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/** Pushed to /topic/ride/{rideId} every time a driver's GPS update lands.
 *  Deliberately just the four fields track.js actually needs -- see the
 *  @JsonIgnore note on Location.ride for why this stays separate from the
 *  Location entity itself rather than broadcasting that directly. */
@Getter
@AllArgsConstructor
public class LocationBroadcast {
    private Long rideId;
    private double latitude;
    private double longitude;
    private LocalDateTime timestamp;
}
