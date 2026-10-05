/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.repository;

import com.deep.WIMB.enums.RideStatus;
import com.deep.WIMB.model.Bus;
import com.deep.WIMB.model.Ride;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs RideRepository.searchByBusNumber against a real (in-memory H2) database. */
@DataJpaTest
class RideRepositoryBusSearchIT {

    @Autowired BusRepository busRepository;
    @Autowired RideRepository rideRepository;

    @BeforeEach
    void data() {
        ride("WB-23A-1245", RideStatus.ACTIVE);
        ride("WB 23A 9999", RideStatus.ACTIVE);
        ride("TEST_123", RideStatus.ACTIVE);
        ride("WB-23A-0001", RideStatus.ENDED);   // ended rides must never be found
    }

    private void ride(String busNumber, RideStatus status) {
        Bus bus = new Bus();
        bus.setBusNumber(busNumber);
        bus = busRepository.save(bus);

        Ride ride = new Ride();
        ride.setBus(bus);
        ride.setRouteKey("A_B");
        ride.setStatus(status);
        ride.setStartTime(LocalDateTime.now());
        rideRepository.save(ride);
    }

    private List<String> search(String normalizedQuery, int limit) {
        return rideRepository
                .searchByBusNumber(RideStatus.ACTIVE, normalizedQuery, PageRequest.of(0, limit))
                .stream().map(r -> r.getBus().getBusNumber()).collect(Collectors.toList());
    }

    @Test
    void matchesIgnoringDashesSpacesAndUnderscores() {
        assertEquals(List.of("WB 23A 9999", "WB-23A-1245"), search("wb23a", 20));
        assertEquals(List.of("WB-23A-1245"), search("1245", 20));
        assertEquals(List.of("TEST_123"), search("test123", 20));
    }

    @Test
    void neverReturnsEndedRides() {
        assertTrue(search("0001", 20).isEmpty());
    }

    @Test
    void aSearchThatMatchesNothingIsEmpty() {
        assertTrue(search("zzzz", 20).isEmpty());
    }

    @Test
    void resultsAreOrderedByBusNumberAndCappedByThePage() {
        assertEquals(List.of("WB 23A 9999"), search("wb23a", 1));
    }
}
