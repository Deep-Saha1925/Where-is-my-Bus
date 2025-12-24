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
    private final GeocodingService geocodingService;

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

    @Transactional
    public Ride cancelRide(Long rideId){
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found."));

        if (ride.getStatus() == RideStatus.ENDED){
            throw new RuntimeException("Ride already ended.");
        }

        ride.setStatus(RideStatus.ENDED);
        ride.setEndTime(LocalDateTime.now());
        return rideRepository.save(ride);
    }

    public List<ActiveRideResponse> getActiveRidesByRoute(String source, String destination) {
        List<Ride> rides = rideRepository
                .findBySourceAndDestinationAndStatus(source, destination, RideStatus.ACTIVE);

        return rides.stream().map(ride -> {
            ActiveRideResponse dto = new ActiveRideResponse();
            dto.setRideId(ride.getId());
            dto.setBusNumber(ride.getBus().getBusNumber());
            dto.setDestination(ride.getDestination());
            dto.setSource(ride.getSource());

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