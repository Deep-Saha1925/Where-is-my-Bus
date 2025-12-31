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
            @RequestParam String source,
            @RequestParam String destination
    ) {
        return rideService.getActiveRidesByRoute(source, destination);
    }

    @PostMapping("/start")
    public Ride startRide(@RequestBody StartRideRequest request) {

        StartRideRequest newRequest = rideService.updateRequest(request);

        return rideService.startRide(newRequest);
    }

    @GetMapping("/active/all")
    public List<ActiveRideResponse> getAllActiveRides() {
        return rideService.getAllActiveRides();
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
