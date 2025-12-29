package com.deep.WIMB.service;

import com.deep.WIMB.dto.ActiveRideResponse;
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
    private final LocationRepository locationRepository;

    @Transactional
    public Ride startRide(StartRideRequest request) {

        Bus bus = busRepository.findByBusNumber(request.getBusNumber())
                .orElseGet(() -> {
                    Bus b = new Bus();
                    b.setBusNumber(request.getBusNumber());
                    return busRepository.save(b);
                });

        // End any existing active ride for this bus
        rideRepository.findByBusAndStatus(bus, RideStatus.ACTIVE)
                .ifPresent(r -> {
                    r.setStatus(RideStatus.ENDED);
                    r.setEndTime(LocalDateTime.now());
                });

        Ride ride = new Ride();
        ride.setBus(bus);
        ride.setRouteKey(request.getRouteKey());
        ride.setStartTime(LocalDateTime.now());
        ride.setStatus(RideStatus.ACTIVE);

        ride = rideRepository.save(ride);

        // Save initial location
        Location loc = new Location();
        loc.setRide(ride);
        loc.setLatitude(request.getLatitude());
        loc.setLongitude(request.getLongitude());
        loc.setTimestamp(LocalDateTime.now());

        locationRepository.save(loc);

        return ride;
    }


    @Transactional
    public Ride cancelRide(Long rideId) {

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        if (ride.getStatus() == RideStatus.ENDED) {
            throw new RuntimeException("Ride already ended");
        }

        ride.setStatus(RideStatus.ENDED);
        ride.setEndTime(LocalDateTime.now());

        return rideRepository.save(ride);
    }

    public List<ActiveRideResponse> getActiveRidesByRoute(String routeKey) {

        List<Ride> rides = rideRepository
                .findByRouteKeyAndStatus(routeKey, RideStatus.ACTIVE);

        return rides.stream().map(ride -> {

            ActiveRideResponse dto = new ActiveRideResponse();
            dto.setRideId(ride.getId());
            dto.setBusNumber(ride.getBus().getBusNumber());
            dto.setRouteKey(ride.getRouteKey());

            // Attach last known location
            locationRepository
                    .findTopByRideIdOrderByTimestampDesc(ride.getId())
                    .ifPresent(loc -> {
                        dto.setLatitude(loc.getLatitude());
                        dto.setLongitude(loc.getLongitude());
                    });

            return dto;
        }).toList();
    }

    public List<ActiveRideResponse> getAllActiveRides(){
        List<Ride> rides = rideRepository.findByStatus(RideStatus.ACTIVE);

        return rides.stream().map(ride -> {
            ActiveRideResponse dto = new ActiveRideResponse();

            dto.setRideId(ride.getId());
            dto.setBusNumber(ride.getBus().getBusNumber());
            dto.setRouteKey(ride.getRouteKey());

            locationRepository
                    .findTopByRideIdOrderByTimestampDesc(ride.getId())
                    .ifPresent(loc -> {
                        dto.setLatitude(loc.getLatitude());
                        dto.setLongitude(loc.getLongitude());
                    });

            return dto;
        }).toList();
    }
}