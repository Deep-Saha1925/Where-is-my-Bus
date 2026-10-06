/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.repository;

import com.deep.WIMB.enums.RideStatus;
import com.deep.WIMB.model.Bus;
import com.deep.WIMB.model.Location;
import com.deep.WIMB.model.Ride;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Runs LocationRepository.deleteOldBatch against a real (in-memory H2) database. */
@DataJpaTest
class LocationRetentionIT {

    @Autowired BusRepository busRepository;
    @Autowired RideRepository rideRepository;
    @Autowired LocationRepository locationRepository;

    private Ride endedRide;
    private Ride activeRide;
    private final LocalDateTime now = LocalDateTime.now();

    @BeforeEach
    void data() {
        endedRide = ride("RET-ENDED", RideStatus.ENDED);
        activeRide = ride("RET-ACTIVE", RideStatus.ACTIVE);

        location(endedRide, now.minusDays(60));   // old, ended ride  -> deleted
        location(endedRide, now.minusDays(40));   // old, ended ride  -> deleted
        location(endedRide, now.minusDays(2));    // recent           -> kept
        location(activeRide, now.minusDays(60));  // old but ACTIVE   -> kept
        location(activeRide, now.minusDays(1));   // recent           -> kept
    }

    private Ride ride(String busNumber, RideStatus status) {
        Bus bus = new Bus();
        bus.setBusNumber(busNumber);
        bus = busRepository.save(bus);

        Ride ride = new Ride();
        ride.setBus(bus);
        ride.setRouteKey("A_B");
        ride.setStatus(status);
        ride.setStartTime(now.minusDays(61));
        return rideRepository.save(ride);
    }

    private void location(Ride ride, LocalDateTime when) {
        Location l = new Location();
        l.setRide(ride);
        l.setLatitude(26.0);
        l.setLongitude(89.0);
        l.setTimestamp(when);
        locationRepository.save(l);
    }

    @Test
    void deletesOldRowsOfEndedRidesButKeepsRecentOnesAndActiveRides() {
        int deleted = locationRepository.deleteOldBatch(now.minusDays(30), 1000);

        assertEquals(2, deleted);
        assertEquals(3, locationRepository.count());
    }

    @Test
    void respectsTheBatchSize() {
        assertEquals(1, locationRepository.deleteOldBatch(now.minusDays(30), 1));
        assertEquals(1, locationRepository.deleteOldBatch(now.minusDays(30), 1));
        assertEquals(0, locationRepository.deleteOldBatch(now.minusDays(30), 1), "nothing left to delete");
    }
}
