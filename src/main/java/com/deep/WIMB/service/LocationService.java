package com.deep.WIMB.service;

import com.deep.WIMB.dto.LocationUpdateRequest;
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
    private final RedisLocationService redisLocationService;

    /**
     * Records a driver's location update. Tries Redis first (it's the hot
     * path reads use), but if Redis can't be reached, saves straight to
     * Postgres instead of losing the update entirely. Without this
     * fallback, a Redis outage meant every location update silently
     * vanished — nothing in Redis, nothing in Postgres either — which is
     * why buses disappeared from every passenger search during the outage
     * with no error visible anywhere except the server's own logs.
     *
     * This no longer re-fetches the Ride row from Postgres. The caller
     * (LocationController / RideController) already confirmed the ride is
     * active via ActiveRideCache right before calling this, so re-querying
     * here was a second DB round trip for the exact same check, on what is
     * the single hottest endpoint in the app. getReferenceById gets a
     * lazy proxy carrying just the ID -- enough to set the foreign key on
     * Location -- without hitting the database at all.
     */
    public Location addLocation(Long rideId, double lat, double lng) {

        Ride ride = rideRepository.getReferenceById(rideId);

        Location location = new Location();
        location.setRide(ride);
        location.setLatitude(lat);
        location.setLongitude(lng);
        location.setTimestamp(LocalDateTime.now());

        boolean savedToRedis = redisLocationService.saveLocationToRedis(location);
        if (!savedToRedis) {
            locationRepository.save(location);
        }
        return location;
    }

    public Location getLastKnownLocation(Long rideId) {

        Location redisLocation = redisLocationService.getLastLocationFromRedis(rideId);
        if (redisLocation != null) {
            return redisLocation;
        }

        return locationRepository
                .findTopByRideIdOrderByTimestampDesc(rideId)
                .orElseThrow(() -> new RuntimeException("Location not found"));
    }
}