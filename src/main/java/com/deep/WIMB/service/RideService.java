//package com.deep.WIMB.service;
//
//import com.deep.WIMB.dto.ActiveRideResponse;
//import com.deep.WIMB.dto.RouteStop;
//import com.deep.WIMB.dto.StartRideRequest;
//import com.deep.WIMB.enums.RideStatus;
//import com.deep.WIMB.model.Bus;
//import com.deep.WIMB.model.Location;
//import com.deep.WIMB.model.Ride;
//import com.deep.WIMB.repository.BusRepository;
//import com.deep.WIMB.repository.LocationRepository;
//import com.deep.WIMB.repository.RideRepository;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//
//@Service
//@RequiredArgsConstructor
//public class RideService {
//
//    private final BusRepository busRepository;
//    private final RideRepository rideRepository;
//    private final LocationRepository locationRepository;
//    private final RouteExcelLoader routeExcelLoader;
//
//    @Transactional
//    public Ride startRide(StartRideRequest request) {
//
//        Bus bus = busRepository.findByBusNumber(request.getBusNumber())
//                .orElseGet(() -> {
//                    Bus b = new Bus();
//                    b.setBusNumber(request.getBusNumber());
//                    return busRepository.save(b);
//                });
//
//        // End any existing active ride for this bus
//        rideRepository.findByBusAndStatus(bus, RideStatus.ACTIVE)
//                .ifPresent(r -> {
//                    r.setStatus(RideStatus.ENDED);
//                    r.setEndTime(LocalDateTime.now());
//                });
//
//        Ride ride = new Ride();
//        ride.setBus(bus);
//        ride.setRouteKey(request.getRouteKey());
//        ride.setStartTime(LocalDateTime.now());
//        ride.setStatus(RideStatus.ACTIVE);
//
//        ride = rideRepository.save(ride);
//
//        // Save initial location
//        Location loc = new Location();
//        loc.setRide(ride);
//        loc.setLatitude(request.getLatitude());
//        loc.setLongitude(request.getLongitude());
//        loc.setTimestamp(LocalDateTime.now());
//
//        locationRepository.save(loc);
//
//        return ride;
//    }
//
//
//    @Transactional
//    public Ride cancelRide(Long rideId) {
//
//        Ride ride = rideRepository.findById(rideId)
//                .orElseThrow(() -> new RuntimeException("Ride not found"));
//
//        if (ride.getStatus() == RideStatus.ENDED) {
//            throw new RuntimeException("Ride already ended");
//        }
//
//        ride.setStatus(RideStatus.ENDED);
//        ride.setEndTime(LocalDateTime.now());
//
//        return rideRepository.save(ride);
//    }
//
////    public List<ActiveRideResponse> getActiveRidesByRoute(String routeKey) {
////
////        List<Ride> rides = rideRepository
////                .findByRouteKeyAndStatus(routeKey, RideStatus.ACTIVE);
////
////        List<Ride> filtered = new ArrayList<>();
////
////        return rides.stream().map(ride -> {
////
////            ActiveRideResponse dto = new ActiveRideResponse();
////            dto.setRideId(ride.getId());
////            dto.setBusNumber(ride.getBus().getBusNumber());
////            dto.setRouteKey(ride.getRouteKey());
////
////            // Attach last known location
////            locationRepository
////                    .findTopByRideIdOrderByTimestampDesc(ride.getId())
////                    .ifPresent(loc -> {
////                        dto.setLatitude(loc.getLatitude());
////                        dto.setLongitude(loc.getLongitude());
////                    });
////
////            return dto;
////        }).toList();
////    }
//
//
////    public List<ActiveRideResponse> getActiveRidesByRoute(String routeKey) {
////
////        String[] parts = routeKey.split("_");
////        String userSource = parts[0];
////        String userDestination = parts[1];
////
////        List<Ride> rides =
////                rideRepository.findByRouteKeyAndStatus(routeKey, RideStatus.ACTIVE);
////
////        List<RouteStop> fullRoute =
////                routeExcelLoader.getRouteBetween(
////                        routeKey,
////                        userSource,
////                        userDestination
////                );
////
////        int userSourceOrder =
////                fullRoute.get(0).getStopOrder();
////
////        return rides.stream()
////                .filter(ride -> {
////
////                    var locOpt =
////                            locationRepository.findTopByRideIdOrderByTimestampDesc(
////                                    ride.getId()
////                            );
////
////                    if (locOpt.isEmpty()) return false;
////
////                    Location loc = locOpt.get();
////
////                    int busCurrentOrder =
////                            getCurrentStopOrder(
////                                    loc.getLatitude(),
////                                    loc.getLongitude(),
////                                    fullRoute
////                            );
////
////                    // CORE RULE
////                    return busCurrentOrder <= userSourceOrder;
////                })
////                .map(ride -> {
////
////                    ActiveRideResponse dto = new ActiveRideResponse();
////                    dto.setRideId(ride.getId());
////                    dto.setBusNumber(ride.getBus().getBusNumber());
////                    dto.setRouteKey(ride.getRouteKey());
////
////                    locationRepository
////                            .findTopByRideIdOrderByTimestampDesc(ride.getId())
////                            .ifPresent(loc -> {
////                                dto.setLatitude(loc.getLatitude());
////                                dto.setLongitude(loc.getLongitude());
////                            });
////
////                    return dto;
////                })
////                .toList();
////    }
//
//
//    public List<ActiveRideResponse> getActiveRidesByRoute(
//            String routeKey,
//            String userSource,
//            String userDestination
//    ) {
//
//        // 1️⃣ Load full route (THIS FIXES YOUR NPE)
//        List<RouteStop> fullRoute =
//                routeExcelLoader.getFullRoute(routeKey);
//
//        if (fullRoute == null) {
//            throw new RuntimeException("Route not loaded: " + routeKey);
//        }
//
//        int userSrcOrder = getStopOrder(fullRoute, userSource);
//        int userDestOrder = getStopOrder(fullRoute, userDestination);
//
//        return rideRepository
//                .findByRouteKeyAndStatus(routeKey, RideStatus.ACTIVE)
//                .stream()
//                .filter(ride -> {
//
//                    // 2️⃣ Find current bus position
//                    Location lastLoc = locationRepository
//                            .findTopByRideIdOrderByTimestampDesc(ride.getId())
//                            .orElse(null);
//
//                    if (lastLoc == null) return false;
//
//                    int busCurrentOrder =
//                            getNearestStopOrder(fullRoute, lastLoc);
//
//                    // 3️⃣ CORE RULE
//                    return busCurrentOrder <= userSrcOrder
//                            && userDestOrder > userSrcOrder;
//                })
//                .map(this::toDto)
//                .toList();
//    }
//
//
//
//    private boolean canSuggestRide(
//            String userRouteKey,
//            String busRouteKey,
//            int currentStopOrder,
//            List<String> stops
//    ) {
//        String[] user = userRouteKey.split("_");
//        String userSrc = user[0];
//        String userDest = user[1];
//
//        int srcIndex = stops.indexOf(userSrc) + 1;
//        int destIndex = stops.indexOf(userDest) + 1;
//
//        // invalid route
//        if (srcIndex == 0 || destIndex == 0) return false;
//
//        // bus already crossed boarding stop
//        if (currentStopOrder > srcIndex) return false;
//
//        // destination must be ahead
//        return destIndex > srcIndex;
//    }
//
//
//
//    public List<ActiveRideResponse> getAllActiveRides(){
//        List<Ride> rides = rideRepository.findByStatus(RideStatus.ACTIVE);
//
//        return rides.stream().map(ride -> {
//            ActiveRideResponse dto = new ActiveRideResponse();
//
//            dto.setRideId(ride.getId());
//            dto.setBusNumber(ride.getBus().getBusNumber());
//            dto.setRouteKey(ride.getRouteKey());
//
//            locationRepository
//                    .findTopByRideIdOrderByTimestampDesc(ride.getId())
//                    .ifPresent(loc -> {
//                        dto.setLatitude(loc.getLatitude());
//                        dto.setLongitude(loc.getLongitude());
//                    });
//
//            return dto;
//        }).toList();
//    }
//
//    ////
//
//
//    private int getStopOrder(List<RouteStop> stops, String stopName) {
//        return stops.stream()
//                .filter(s -> s.getStopName().equalsIgnoreCase(stopName))
//                .findFirst()
//                .map(RouteStop::getStopOrder)
//                .orElseThrow(() ->
//                        new RuntimeException("Stop not found: " + stopName));
//    }
//
//    private int getNearestStopOrder(
//            List<RouteStop> stops,
//            Location loc
//    ) {
//        RouteStop nearest = null;
//        double minDist = Double.MAX_VALUE;
//
//        for (RouteStop s : stops) {
//            double d = distance(
//                    s.getLatitude(), s.getLongitude(),
//                    loc.getLatitude(), loc.getLongitude()
//            );
//            if (d < minDist) {
//                minDist = d;
//                nearest = s;
//            }
//        }
//        return nearest.getStopOrder();
//    }
//
//
//
//
////////
//
//    private int getCurrentStopOrder(
//            double busLat,
//            double busLng,
//            List<RouteStop> stops
//    ) {
//        RouteStop nearest = null;
//        double minDistance = Double.MAX_VALUE;
//
//        for (RouteStop stop : stops) {
//            double dist = distance(
//                    busLat, busLng,
//                    stop.getLatitude(), stop.getLongitude()
//            );
//
//            if (dist < minDistance) {
//                minDistance = dist;
//                nearest = stop;
//            }
//        }
//
//        return nearest != null ? nearest.getStopOrder() : Integer.MAX_VALUE;
//    }
//
//    // Haversine distance
//    private double distance(
//            double lat1, double lon1,
//            double lat2, double lon2
//    ) {
//        double R = 6371; // km
//        double dLat = Math.toRadians(lat2 - lat1);
//        double dLon = Math.toRadians(lon2 - lon1);
//        double a =
//                Math.sin(dLat / 2) * Math.sin(dLat / 2) +
//                        Math.cos(Math.toRadians(lat1)) *
//                                Math.cos(Math.toRadians(lat2)) *
//                                Math.sin(dLon / 2) * Math.sin(dLon / 2);
//
//        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
//    }
//
//
//
//}




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

        List<RouteStop> routeSegment =
                routeExcelLoader.getRouteBetween(
                        userSource,
                        userDestination
                );

        int userSourceOrder = routeSegment.get(0).getStopOrder();

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

                    List<RouteStop> fullRoute = routeExcelLoader.getFullRoute();

                    int busCurrentOrder = findStopOrder(fullRoute, lastLoc);

                    // IMPORTANT RULE
                    return busCurrentOrder <= userSourceOrder;
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

    // ================= HELPER METHODS =================

//    private int findNearestStopOrder(
//            List<RouteStop> stops,
//            Location loc
//    ) {
//        RouteStop nearest = null;
//        double minDist = Double.MAX_VALUE;
//
//        for (RouteStop s : stops) {
//            double d = distance(
//                    s.getLatitude(), s.getLongitude(),
//                    loc.getLatitude(), loc.getLongitude()
//            );
//            if (d < minDist) {
//                minDist = d;
//                nearest = s;
//            }
//        }
//
//        return nearest != null
//                ? nearest.getStopOrder()
//                : Integer.MAX_VALUE;
//    }
//
//    // ================= DISTANCE =================
//
//    private double distance(
//            double lat1, double lon1,
//            double lat2, double lon2
//    ) {
//        double R = 6371;
//        double dLat = Math.toRadians(lat2 - lat1);
//        double dLon = Math.toRadians(lon2 - lon1);
//
//        double a =
//                Math.sin(dLat / 2) * Math.sin(dLat / 2) +
//                        Math.cos(Math.toRadians(lat1)) *
//                                Math.cos(Math.toRadians(lat2)) *
//                                Math.sin(dLon / 2) * Math.sin(dLon / 2);
//
//        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
//    }
}
