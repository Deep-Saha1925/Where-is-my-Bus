package com.deep.WIMB.controller;

import com.deep.WIMB.dto.ActiveRideResponse;
import com.deep.WIMB.dto.LocationUpdateRequest;
import com.deep.WIMB.dto.StartRideRequest;
import com.deep.WIMB.model.Location;
import com.deep.WIMB.model.Ride;
import com.deep.WIMB.service.LocationService;
import com.deep.WIMB.service.RideService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ride")
@RequiredArgsConstructor
public class RideController {

    private final RideService rideService;
    private final LocationService locationService;

    @GetMapping("/active")
    public List<ActiveRideResponse> getActiveRides(
            @RequestParam String routeKey
    ) {
        return rideService.getActiveRidesByRoute(routeKey);
    }

    @PostMapping("/start")
    public Ride startRide(@RequestBody StartRideRequest request) {

        Ride ride = rideService.startRide(request);

        // Create FIRST location
        locationService.addLocation(
                ride.getId(),
                request.getLatitude(),
                request.getLongitude()
        );

        return ride;
    }

    @PostMapping("/location")
    public Location updateLocation(@RequestBody LocationUpdateRequest request) {
        return locationService.addLocation(
                request.getRideId(),
                request.getLatitude(),
                request.getLongitude()
        );
    }

    @PutMapping("/cancel/{rideId}")
    public Ride cancelRide(@PathVariable Long rideId) {
        return rideService.cancelRide(rideId);
    }

}
