package com.deep.WIMB.service;

import com.deep.WIMB.dto.ActiveRideResponse;
import com.deep.WIMB.dto.RouteStop;
import com.deep.WIMB.dto.StartRideRequest;
import com.deep.WIMB.enums.RideStatus;
import com.deep.WIMB.model.Bus;
import com.deep.WIMB.model.Location;
import com.deep.WIMB.model.Ride;
import com.deep.WIMB.repository.BusRepository;
import com.deep.WIMB.repository.LocationRepository;
import com.deep.WIMB.repository.RideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RideService {

    private final BusRepository busRepository;
    private final RideRepository rideRepository;
    private final LocationRepository locationRepository;
    private final RouteExcelLoader routeExcelLoader;
    private final RedisLocationService redisLocationService; // ← NEW

    // ============ UPDATE RIDE REQUEST ================
    public StartRideRequest updateRequest(StartRideRequest request) {
        StartRideRequest updatedRequest = new StartRideRequest();
        List<RouteStop> allRoute = routeExcelLoader.getFullRoute();
        String src = request.getRouteKey().split("_")[0];

        for (RouteStop stop : allRoute) {
            if (stop.getStopName().equalsIgnoreCase(src)) {
                updatedRequest.setRouteKey(request.getRouteKey());
                updatedRequest.setBusNumber(request.getBusNumber());
                updatedRequest.setLatitude(stop.getLatitude());
                updatedRequest.setLongitude(stop.getLongitude());
                break;
            }
        }

        return updatedRequest;
    }

    // ================= START RIDE =================

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
                    // ── flush Redis before ending old ride ──
                    flushRideFromRedisToMySQL(r.getId());
                    r.setStatus(RideStatus.ENDED);
                    r.setEndTime(LocalDateTime.now());
                });

        Ride ride = new Ride();
        ride.setBus(bus);
        ride.setRouteKey(request.getRouteKey());
        ride.setStartTime(LocalDateTime.now());
        ride.setStatus(RideStatus.ACTIVE);

        ride = rideRepository.save(ride);

        Location loc = new Location();
        loc.setRide(ride);
        loc.setLatitude(request.getLatitude());
        loc.setLongitude(request.getLongitude());
        loc.setTimestamp(LocalDateTime.now());

        // Save to Redis instead of MySQL directly
        redisLocationService.saveLocationToRedis(loc);

        return ride;
    }

    // ================= CANCEL RIDE =================

    @Transactional
    public Ride cancelRide(Long rideId) {

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        if (ride.getStatus() == RideStatus.ENDED) {
            throw new RuntimeException("Ride already ended");
        }

        // Flush Redis → MySQL before ending ride so no data is lost
        flushRideFromRedisToMySQL(rideId);

        ride.setStatus(RideStatus.ENDED);
        ride.setEndTime(LocalDateTime.now());

        return rideRepository.save(ride);
    }

    // ================= CORE LOGIC =================

    public List<ActiveRideResponse> getActiveRidesByRoute(
            String userSource,
            String userDestination
    ) {

        List<RouteStop> fullRoute = routeExcelLoader.getFullRoute();

        int sourceOrder = routeExcelLoader.getStopOrderByName(userSource);
        int destOrder = routeExcelLoader.getStopOrderByName(userDestination);

        boolean isForward = sourceOrder < destOrder;

        return rideRepository
                .findByStatus(RideStatus.ACTIVE)
                .stream()
                .map(ride -> {

                    //Redis-first, fallback to MySQL
                    Location lastLoc = getLatestLocation(ride.getId());

                    if (lastLoc == null) return null;

                    int busOrder = findStopOrder(fullRoute, lastLoc);

                    boolean allowed;
                    if (isForward) {
                        allowed = busOrder <= sourceOrder && sourceOrder <= destOrder;
                    } else {
                        allowed = busOrder >= sourceOrder && sourceOrder >= destOrder;
                    }

                    if (!allowed) return null;

                    RouteStop busStop = fullRoute.stream()
                            .filter(s -> s.getStopOrder() == busOrder)
                            .findFirst()
                            .orElse(null);

                    RouteStop sourceStop = fullRoute.stream()
                            .filter(s -> s.getStopOrder() == sourceOrder)
                            .findFirst()
                            .orElse(null);

                    if (busStop == null || sourceStop == null) return null;

                    double remainingDistance =
                            Math.abs(sourceStop.getDistanceFromStartKm()
                                    - busStop.getDistanceFromStartKm());

                    ActiveRideResponse dto = toDto(ride);
                    dto.setRemainingDistanceKm(remainingDistance);

                    return dto;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ================= ALL ACTIVE RIDES =================

    public List<ActiveRideResponse> getAllActiveRides() {
        return rideRepository.findByStatus(RideStatus.ACTIVE)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ================= HELPER: Redis-first latest location =================

    private Location getLatestLocation(Long rideId) {

        // 1. Try Redis first
        List<Location> redisLocations = redisLocationService.getLocationsFromRedis(rideId);
        if (!redisLocations.isEmpty()) {
            // Last element is the most recent (we use rightPush)
            return redisLocations.get(redisLocations.size() - 1);
        }

        // 2. Fallback to MySQL
        return locationRepository
                .findTopByRideIdOrderByTimestampDesc(rideId)
                .orElse(null);
    }

    // ================= HELPER: Flush Redis → MySQL =================

    private void flushRideFromRedisToMySQL(Long rideId) {
        List<Location> locations = redisLocationService.getLocationsFromRedis(rideId);
        if (!locations.isEmpty()) {
            locationRepository.saveAll(locations);
            redisLocationService.clearLocationsFromRedis(rideId);
            log.info("Flushed {} locations for rideId={} to MySQL", locations.size(), rideId);
        }
    }

    // ================= HELPER: Find stop order =================

    private int findStopOrder(List<RouteStop> fullRoute, Location lastLoc) {

        if (lastLoc == null || fullRoute == null || fullRoute.isEmpty()) {
            return -1;
        }

        double lat = lastLoc.getLatitude();
        double lon = lastLoc.getLongitude();

        for (RouteStop stop : fullRoute) {
            if (Double.compare(stop.getLatitude(), lat) == 0 &&
                    Double.compare(stop.getLongitude(), lon) == 0) {
                return stop.getStopOrder();
            }
        }

        return -1;
    }

    // ================= DTO MAPPER =================

    private ActiveRideResponse toDto(Ride ride) {

        ActiveRideResponse dto = new ActiveRideResponse();
        dto.setRideId(ride.getId());
        dto.setBusNumber(ride.getBus().getBusNumber());
        dto.setRouteKey(ride.getRouteKey());

        //Redis-first, fallback to MySQL
        Location latest = getLatestLocation(ride.getId());
        if (latest != null) {
            dto.setLatitude(latest.getLatitude());
            dto.setLongitude(latest.getLongitude());
        }

        return dto;
    }
}