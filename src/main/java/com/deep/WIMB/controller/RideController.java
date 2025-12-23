package com.deep.WIMB.controller;

import com.deep.WIMB.dto.StartRideRequest;
import com.deep.WIMB.model.Ride;
import com.deep.WIMB.service.RideService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ride")
@RequiredArgsConstructor
public class RideController {

    private final RideService rideService;

    // Driver starts the ride
    @PostMapping("/start")
    public Ride startRide(@RequestBody StartRideRequest request){
        return rideService.startRide(
                request.getBusNumber(),
                request.getSource(),
                request.getDestination()
        );
    }

    //user searches buses on route
    @GetMapping("/active")
    public List<Ride> getActiveRides(
            @RequestParam String source,
            @RequestParam String destination
    ){
        return rideService.getActiveRidesByRoute(source, destination);
    }

}
