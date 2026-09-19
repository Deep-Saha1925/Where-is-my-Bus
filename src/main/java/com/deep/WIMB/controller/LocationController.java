package com.deep.WIMB.controller;

import com.deep.WIMB.dto.LocationUpdateRequest;
import com.deep.WIMB.exception.DriverNotVerifiedException;
import com.deep.WIMB.model.Location;
import com.deep.WIMB.service.ActiveRideCache;
import com.deep.WIMB.service.DriverTokenService;
import com.deep.WIMB.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;
    private final ActiveRideCache activeRideCache;
    private final DriverTokenService driverTokenService;

    // Driver sends GPS. Validated against the in-memory active-ride cache
    // rather than a DB lookup -- this endpoint fires every few seconds per
    // active bus, so it's the hottest path in the app; see ActiveRideCache
    // for why a DB round trip here doesn't scale past a handful of buses.
    @PostMapping("/update")
    public Location updateLocation(@RequestBody LocationUpdateRequest request,
                                   @RequestHeader(value = "X-Driver-Token", required = false) String driverToken) {

        String tokenBus = driverTokenService.resolveBusNumber(driverToken);
        if (tokenBus == null || !activeRideCache.isActiveForBus(request.getRideId(), tokenBus)) {
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