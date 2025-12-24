package com.deep.WIMB.service;

import com.deep.WIMB.dto.StartRideRequest;
import com.deep.WIMB.enums.RideStatus;
import com.deep.WIMB.model.Bus;
import com.deep.WIMB.model.Location;
import com.deep.WIMB.model.Ride;
import com.deep.WIMB.repository.BusRepository;
import com.deep.WIMB.repository.LocationRepository;
import com.deep.WIMB.repository.RideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RideService {

    private final BusRepository busRepository;
    private final RideRepository rideRepository;

    @Transactional
    public Ride startRide(StartRideRequest request) {

        Bus bus = busRepository.findByBusNumber(request.getBusNumber())
                .orElseGet(() -> {
                    Bus newBus = new Bus();
                    newBus.setBusNumber(request.getBusNumber());
                    return busRepository.save(newBus);
                });

        // End previous active ride
        rideRepository.findByBusAndStatus(bus, RideStatus.ACTIVE)
                .ifPresent(ride -> {
                    ride.setStatus(RideStatus.ENDED);
                    ride.setEndTime(LocalDateTime.now());
                });

        Ride ride = new Ride();
        ride.setBus(bus);
        ride.setSource(request.getSource());
        ride.setDestination(request.getDestination());
        ride.setStartTime(LocalDateTime.now());
        ride.setStatus(RideStatus.ACTIVE);

        return rideRepository.save(ride);
    }

    public List<Ride> getActiveRidesByRoute(String source, String destination) {
        return rideRepository.findBySourceAndDestinationAndStatus(
                source, destination, RideStatus.ACTIVE);
    }
}