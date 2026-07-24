package com.deep.WIMB.controller;

import com.deep.WIMB.dto.ActiveRideResponse;
import com.deep.WIMB.dto.LocationUpdateRequest;
import com.deep.WIMB.dto.StartRideRequest;
import com.deep.WIMB.exception.DriverNotVerifiedException;
import com.deep.WIMB.model.Location;
import com.deep.WIMB.model.Ride;
import com.deep.WIMB.service.DriverTokenService;
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
    private final DriverTokenService driverTokenService;

    @GetMapping("/active")
    public List<ActiveRideResponse> getActiveRides(
            @RequestParam String source,
            @RequestParam String destination
    ) {
        return rideService.getActiveRidesByRoute(source, destination);
    }

    @PostMapping("/start")
    public Ride startRide(@RequestBody StartRideRequest request,
                          @RequestHeader(value = "X-Driver-Token", required = false) String driverToken) {

        String tokenBus = driverTokenService.resolveBusNumber(driverToken);
        if (tokenBus == null || !tokenBus.equalsIgnoreCase(request.getBusNumber())) {
            throw new DriverNotVerifiedException("Driver not verified for bus " + request.getBusNumber());
        }

        StartRideRequest newRequest = rideService.updateRequest(request);
        return rideService.startRide(newRequest);
    }

    @GetMapping("/active/all")
    public List<ActiveRideResponse> getAllActiveRides() {
        return rideService.getAllActiveRides();
    }

    @PostMapping("/location")
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

    @PutMapping("/cancel/{rideId}")
    public Ride cancelRide(@PathVariable Long rideId,
                           @RequestHeader(value = "X-Driver-Token", required = false) String driverToken) {

        Ride ride = rideService.getRideById(rideId);
        String tokenBus = driverTokenService.resolveBusNumber(driverToken);
        if (ride == null || tokenBus == null || !tokenBus.equalsIgnoreCase(ride.getBus().getBusNumber())) {
            throw new DriverNotVerifiedException("Driver not verified for this ride");
        }

        return rideService.cancelRide(rideId);
    }
}