package com.deep.WIMB.service;

import com.deep.WIMB.dto.LocationUpdateRequest;
import com.deep.WIMB.enums.RideStatus;
import com.deep.WIMB.model.Location;
import com.deep.WIMB.model.Ride;
import com.deep.WIMB.repository.LocationRepository;
import com.deep.WIMB.repository.RideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;
    private final RideRepository rideRepository;

    public Location updateLocation(LocationUpdateRequest request){
        Ride ride = rideRepository.findById(request.getRideId())
                .orElseThrow(() -> new RuntimeException("Ride not found!!"));

        if (ride.getStatus() != RideStatus.ACTIVE){
            throw new RuntimeException("Ride is not Active.");
        }

        Location location = new Location();
        location.setRide(ride);
        location.setLatitude(request.getLatitude());
        location.setLongitude(request.getLongitude());
        location.setSpeed(request.getSpeed());
        location.setTimestamp(LocalDateTime.now());

        return locationRepository.save(location);
    }

    public Location getLastKnownLocation(Long rideId){
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("No ride found"));

        return locationRepository
                .findTopByRideOrderByTimestampDesc(ride)
                .orElseThrow(() -> new RuntimeException("Location not found"));
    }
}
