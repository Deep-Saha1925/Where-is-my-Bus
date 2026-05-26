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
    private final RedisLocationService redisLocationService;

    // ============ UPDATE RIDE REQUEST ================
    public StartRideRequest updateRequest(StartRideRequest request) {
        StartRideRequest updatedRequest = new StartRideRequest();
        List<RouteStop> allRoute = routeExcelLoader.getFullRoute();

        String routeKey = request.getRouteKey();
        int separatorIdx = routeKey.indexOf("_");
        if (separatorIdx == -1) {
            throw new RuntimeException("Invalid routeKey format: " + routeKey);
        }
        String src = routeKey.substring(0, separatorIdx).trim();

        for (RouteStop stop : allRoute) {
            if (stop.getStopName().equalsIgnoreCase(src)) {
                updatedRequest.setRouteKey(routeKey);
                updatedRequest.setBusNumber(request.getBusNumber());
                updatedRequest.setLatitude(stop.getLatitude());
                updatedRequest.setLongitude(stop.getLongitude());
                break;
            }
        }

        // If no stop matched, still pass through the request as-is
        if (updatedRequest.getBusNumber() == null) {
            System.err.println("⚠️ Source stop not found in route: " + src);
            updatedRequest.setRouteKey(routeKey);
            updatedRequest.setBusNumber(request.getBusNumber());
            updatedRequest.setLatitude(request.getLatitude());
            updatedRequest.setLongitude(request.getLongitude());
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

        rideRepository.findByBusAndStatus(bus, RideStatus.ACTIVE)
                .ifPresent(r -> {
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

        flushRideFromRedisToMySQL(rideId);
        ride.setStatus(RideStatus.ENDED);
        ride.setEndTime(LocalDateTime.now());
        return rideRepository.save(ride);
    }

    // ================= CORE LOGIC =================
    public List<ActiveRideResponse> getActiveRidesByRoute(
            String userSource, String userDestination) {

        List<RouteStop> fullRoute = routeExcelLoader.getFullRoute();

        int sourceOrder;
        int destOrder;

        try {
            sourceOrder = routeExcelLoader.getStopOrderByName(userSource);
            destOrder   = routeExcelLoader.getStopOrderByName(userDestination);
            System.out.println("sourceOrder=" + sourceOrder + " destOrder=" + destOrder);
        } catch (RuntimeException e) {
            System.err.println("❌ Stop not found: " + e.getMessage());
            return List.of();
        }

        boolean isForward = sourceOrder < destOrder;
        List<Ride> activeRides = rideRepository.findByStatus(RideStatus.ACTIVE);

        return activeRides.stream()
                .map(ride -> {

                    // Extract bus source & destination from routeKey
                    String rideRouteKey = ride.getRouteKey();
                    String busSource = null;
                    String busDestination = null;

                    if (rideRouteKey != null && rideRouteKey.contains("_")) {
                        int idx = rideRouteKey.indexOf("_");
                        busSource      = rideRouteKey.substring(0, idx).trim();
                        busDestination = rideRouteKey.substring(idx + 1).trim();
                    }

                    // Get bus destination stop order
                    int busDestOrder = -1;
                    try {
                        if (busDestination != null) {
                            busDestOrder = routeExcelLoader.getStopOrderByName(busDestination);
                        }
                    } catch (RuntimeException e) {
                        System.err.println("Bus dest stop not found: " + busDestination);
                    }

                    // bus destination order must be >= passenger destination order (forward)
                    //      or bus destination order must be <= passenger destination order (backward)
                    if (busDestOrder != -1) {
                        if (isForward && busDestOrder < destOrder) {
                            System.out.println("Ride " + ride.getId()
                                    + " — bus dest=" + busDestination
                                    + " won't reach passenger dest. Skipping.");
                            return null;
                        }
                        if (!isForward && busDestOrder > destOrder) {
                            System.out.println("Ride " + ride.getId()
                                    + " — bus dest=" + busDestination
                                    + " won't reach passenger dest. Skipping.");
                            return null;
                        }
                    }

                    Location lastLoc = getLatestLocation(ride.getId());
                    if (lastLoc == null) return null;

                    int busOrder = findStopOrder(fullRoute, lastLoc);
                    System.out.println("Ride " + ride.getId() + " busOrder=" + busOrder);
                    if (busOrder == -1) return null;

                    // Forward: bus hasn't passed passenger source yet
                    // Backward: bus hasn't passed passenger source yet (in reverse direction)
                    boolean allowed;
                    if (isForward) {
                        allowed = busOrder <= sourceOrder;
                    } else {
                        allowed = busOrder >= sourceOrder;
                    }

                    if (!allowed) return null;

                    RouteStop busStop = fullRoute.stream()
                            .filter(s -> s.getStopOrder() == busOrder)
                            .findFirst().orElse(null);
                    RouteStop sourceStop = fullRoute.stream()
                            .filter(s -> s.getStopOrder() == sourceOrder)
                            .findFirst().orElse(null);

                    if (busStop == null || sourceStop == null) return null;

                    double remainingDistance = Math.abs(
                            sourceStop.getDistanceFromStartKm()
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
        Location redisLocation = redisLocationService.getLastLocationFromRedis(rideId);
        if (redisLocation != null) {
            return redisLocation;
        }
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

    // ================= HELPER: Find nearest stop =================
    private int findStopOrder(List<RouteStop> fullRoute, Location lastLoc) {
        if (lastLoc == null || fullRoute == null || fullRoute.isEmpty()) return -1;

        double lat = lastLoc.getLatitude();
        double lon = lastLoc.getLongitude();

        RouteStop nearest  = null;
        double minDistance = Double.MAX_VALUE;

        for (RouteStop stop : fullRoute) {
            double dist = haversine(lat, lon, stop.getLatitude(), stop.getLongitude());
            if (dist < minDistance) {
                minDistance = dist;
                nearest     = stop;
            }
        }

        return (nearest != null && minDistance <= 1.0) ? nearest.getStopOrder() : -1;
    }

    // ================= HELPER: Haversine =================
    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a    = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // ================= DTO MAPPER =================
    private ActiveRideResponse toDto(Ride ride) {
        ActiveRideResponse dto = new ActiveRideResponse();
        dto.setRideId(ride.getId());
        dto.setBusNumber(ride.getBus().getBusNumber());
        dto.setRouteKey(ride.getRouteKey());

        Location latest = getLatestLocation(ride.getId());
        if (latest != null) {
            dto.setLatitude(latest.getLatitude());
            dto.setLongitude(latest.getLongitude());
        }
        return dto;
    }
}