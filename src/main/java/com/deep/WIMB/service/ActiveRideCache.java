package com.deep.WIMB.service;

import com.deep.WIMB.enums.RideStatus;
import com.deep.WIMB.model.Ride;
import com.deep.WIMB.repository.RideRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory map of rideId -> busNumber, for every currently ACTIVE ride.
 *
 * Why this exists: every single driver GPS ping (every ~1-3s per active
 * bus, sometimes faster depending on how often the browser's
 * watchPosition fires) used to call rideRepository.findById(rideId) twice
 * — once in the controller to check the driver token belongs to this
 * ride's bus, and again inside LocationService.addLocation to re-check
 * the ride is still ACTIVE. That's a real Postgres round trip, twice, on
 * the hottest path in the whole app — with only 5 pooled connections
 * (see application.properties), a handful of concurrently active buses
 * is enough to start queueing requests behind that pool.
 *
 * The GPS write itself already goes to Redis, not Postgres, for exactly
 * this reason (see RedisLocationService) — this cache closes the same
 * gap for the *auth check* that happens right before that write.
 *
 * Kept deliberately simple (a plain ConcurrentHashMap, single instance)
 * because the app runs as a single Render instance for now. If/when this
 * moves to multiple instances, this needs to move to Redis instead (same
 * idea as DriverTokenService) so every instance sees the same set of
 * active rides — see the note on loadFromDatabase() below.
 */
@Service
@RequiredArgsConstructor
public class ActiveRideCache {

    private final RideRepository rideRepository;

    private final ConcurrentHashMap<Long, String> activeRides = new ConcurrentHashMap<>();

    /** Rebuilds the cache from the database on startup, so a restart (very
     *  much a normal event on Render's free tier) doesn't leave already
     *  in-progress rides unable to send further location updates until
     *  their driver restarts the ride. */
    @PostConstruct
    public void loadFromDatabase() {
        List<Ride> active = rideRepository.findByStatus(RideStatus.ACTIVE);
        for (Ride ride : active) {
            activeRides.put(ride.getId(), ride.getBus().getBusNumber().trim().toUpperCase());
        }
        System.out.println("ActiveRideCache: loaded " + activeRides.size() + " active ride(s) from the database");
    }

    public void put(Long rideId, String busNumber) {
        activeRides.put(rideId, busNumber.trim().toUpperCase());
    }

    public void remove(Long rideId) {
        activeRides.remove(rideId);
    }

    /** Bus number this ride belongs to, if it's currently active — or null
     *  if the ride doesn't exist, has ended, or was never in the cache. */
    public String getBusNumber(Long rideId) {
        if (rideId == null) return null;
        return activeRides.get(rideId);
    }

    public boolean isActiveForBus(Long rideId, String busNumber) {
        String cached = getBusNumber(rideId);
        return cached != null && busNumber != null && cached.equalsIgnoreCase(busNumber.trim());
    }
}
