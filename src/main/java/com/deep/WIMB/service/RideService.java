package com.deep.WIMB.service;

import com.deep.WIMB.enums.RideStatus;
import com.deep.WIMB.model.Bus;
import com.deep.WIMB.model.Ride;
import com.deep.WIMB.repository.BusRepository;
import com.deep.WIMB.repository.RideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RideService {

    private final BusRepository busRepository;
    private final RideRepository rideRepository;

    public Ride startRide(String busNumber, String source, String destination){
        Bus bus = busRepository.findByBusNumber(busNumber)
                .orElseGet(() -> {
                    Bus newBus = new Bus();
                    newBus.setBusNumber(busNumber);
                    return busRepository.save(newBus);
                });

        // end previous ride if exists
        rideRepository.findByBusAndStatus(bus, RideStatus.ACTIVE)
                .ifPresent(ride -> {
                    ride.setStatus(RideStatus.ENDED);
                    ride.setEndTime(LocalDateTime.now());
                    rideRepository.save(ride);
                });

        Ride ride = new Ride();
        ride.setBus(bus);
        ride.setSource(source);
        ride.setDestination(destination);
        ride.setStartTime(LocalDateTime.now());
        ride.setStatus(RideStatus.ACTIVE);

        return rideRepository.save(ride);
    }

    public List<Ride> getActiveRidesByRoute(String source, String destination) {
        return rideRepository.findBySourceAndDestinationAndStatus(
                source, destination, RideStatus.ACTIVE);
    }
}
