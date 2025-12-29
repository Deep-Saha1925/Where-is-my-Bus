package com.deep.WIMB.service;

import com.deep.WIMB.dto.ActiveRideResponse;
import com.deep.WIMB.dto.StartRideRequest;
import com.deep.WIMB.enums.RideStatus;
import com.deep.WIMB.model.Bus;
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

        //  Find or create Bus
        Bus bus = busRepository.findByBusNumber(request.getBusNumber())
                .orElseGet(() -> {
                    Bus newBus = new Bus();
                    newBus.setBusNumber(request.getBusNumber());
                    return busRepository.save(newBus);
                });

        //  End any existing active ride for this bus
        rideRepository.findByBusAndStatus(bus, RideStatus.ACTIVE)
                .ifPresent(oldRide -> {
                    oldRide.setStatus(RideStatus.ENDED);
                    oldRide.setEndTime(LocalDateTime.now());
                });

        //  Create new Ride (ROUTE-BASED, not source/dest based)
        Ride ride = new Ride();
        ride.setBus(bus);
        ride.setRouteKey(request.getRouteKey());   // 🔥 IMPORTANT
        ride.setStartTime(LocalDateTime.now());
        ride.setStatus(RideStatus.ACTIVE);

        return rideRepository.save(ride);
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
}