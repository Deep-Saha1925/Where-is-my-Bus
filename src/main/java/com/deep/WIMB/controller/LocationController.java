package com.deep.WIMB.controller;

import com.deep.WIMB.dto.LocationUpdateRequest;
import com.deep.WIMB.exception.DriverNotVerifiedException;
import com.deep.WIMB.model.Location;
import com.deep.WIMB.model.Ride;
import com.deep.WIMB.service.DriverTokenService;
import com.deep.WIMB.service.LocationService;
import com.deep.WIMB.service.RideService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;
    private final RideService rideService;
    private final DriverTokenService driverTokenService;

    // Driver sends GPS
    @PostMapping("/update")
    public Location updateLocation(@RequestBody LocationUpdateRequest request,
                                   @RequestHeader(value = "X-Driver-Token", required = false) String driverToken) {

        Ride ride = rideService.getRideById(request.getRideId());
        String tokenBus = driverTokenService.resolveBusNumber(driverToken);
        if (ride == null || tokenBus == null || !tokenBus.equalsIgnoreCase(ride.getBus().getBusNumber())) {
            throw new DriverNotVerifiedException("Driver not verified for this ride");
        }

        return locationService.addLocation(
                request.getRideId(),
                request.getLatitude(),
                request.getLongitude()
        );
    }

    // passenger fetches last known location
    @GetMapping("/last-loc/{rideId}")
    public Location getLastLocation(@PathVariable Long rideId){
        return locationService.getLastKnownLocation(rideId);
    }
}