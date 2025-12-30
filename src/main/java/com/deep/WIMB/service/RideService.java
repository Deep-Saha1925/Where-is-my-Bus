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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RideService {

    private final BusRepository busRepository;
    private final RideRepository rideRepository;
    private final LocationRepository locationRepository;
    private final RouteExcelLoader routeExcelLoader;

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

        locationRepository.save(loc);

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
                .filter(ride -> {

                    Location lastLoc =
                            locationRepository
                                    .findTopByRideIdOrderByTimestampDesc(
                                            ride.getId()
                                    )
                                    .orElse(null);

                    if (lastLoc == null) return false;

                    int busOrder = findStopOrder(fullRoute, lastLoc);

                    if (isForward) {
                    /*
                      FORWARD RULE:
                      bus must NOT have crossed source
                      AND destination must be ahead
                     */
                        return busOrder <= sourceOrder
                                && sourceOrder <= destOrder;

                    } else {
                    /*
                      BACKWARD RULE:
                      bus must NOT have crossed source
                      AND destination must be behind
                     */
                        return busOrder >= sourceOrder
                                && sourceOrder >= destOrder;
                    }
                })
                .map(this::toDto)
                .collect(Collectors.toList());
    }


    // ================= ALL ACTIVE RIDES =================

    public List<ActiveRideResponse> getAllActiveRides() {

        return rideRepository.findByStatus(RideStatus.ACTIVE)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

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

        return -1; // stop not found
    }


    // ================= DTO MAPPER =================

    private ActiveRideResponse toDto(Ride ride) {

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
    }

}