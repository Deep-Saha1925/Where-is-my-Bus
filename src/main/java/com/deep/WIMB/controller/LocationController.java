package com.deep.WIMB.controller;

import com.deep.WIMB.dto.LocationUpdateRequest;
import com.deep.WIMB.model.Location;
import com.deep.WIMB.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    // Driver sends GPS
    @PostMapping("/update")
    public Location updateLocation(@RequestBody LocationUpdateRequest request){
        return locationService.addLocation(
                request.getRideId(),
                request.getLatitude(),
                request.getLongitude(),
                request.getSpeed()
        );
    }

    // user fetches last user location
    @GetMapping("/last-loc/{rideId}")
    public Location getLastLocation(@PathVariable Long rideId){
        return locationService.getLastKnownLocation(rideId);
    }

}
