/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import com.deep.WIMB.TestFields;
import com.deep.WIMB.dto.ActiveRideResponse;
import com.deep.WIMB.enums.RideStatus;
import com.deep.WIMB.model.Bus;
import com.deep.WIMB.model.Location;
import com.deep.WIMB.model.Ride;
import com.deep.WIMB.repository.BusRepository;
import com.deep.WIMB.repository.LocationRepository;
import com.deep.WIMB.repository.RideRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Ghost-ride handling and the bus-number search in RideService, with the database and Redis mocked. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RideServiceGhostRidesTest {

    @Mock BusRepository busRepository;
    @Mock RideRepository rideRepository;
    @Mock LocationRepository locationRepository;
    @Mock RouteExcelLoader routeExcelLoader;
    @Mock RedisLocationService redisLocationService;
    @Mock ActiveRideCache activeRideCache;
    @Mock BusSearchCache busSearchCache;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock LatestLocationResolver latestLocationResolver;
    @Spy LastLocationCache lastLocationCache = new LastLocationCache();
    @Spy RideActivityTracker activityTracker = new RideActivityTracker();

    @InjectMocks RideService rideService;

    @BeforeEach
    void configureTracker() {
        TestFields.set(activityTracker, "staleAfterSeconds", 300L);      // hidden after 5 min
        TestFields.set(activityTracker, "autoEndAfterSeconds", 1800L);   // ended after 30 min
    }

    private static Ride activeRide(long id, String busNumber) {
        Bus bus = new Bus();
        bus.setBusNumber(busNumber);
        Ride ride = new Ride();
        ride.setId(id);
        ride.setBus(bus);
        ride.setRouteKey("A_B");
        ride.setStatus(RideStatus.ACTIVE);
        ride.setStartTime(LocalDateTime.now().minusHours(2));
        return ride;
    }

    private static long minutesAgo(long minutes) {
        return System.currentTimeMillis() - minutes * 60_000L;
    }

    /* ─── auto-ending ghost rides ─────────────────────────────────── */

    @Test
    void endsARideSilentLongerThanTheAutoEndLimit() {
        Ride ride = activeRide(1L, "WB-1");
        when(rideRepository.findByStatus(RideStatus.ACTIVE)).thenReturn(List.of(ride));
        activityTracker.touch(1L, minutesAgo(31));

        int ended = rideService.endStaleRides();

        assertEquals(1, ended);
        assertEquals(RideStatus.ENDED, ride.getStatus());
        assertNotNull(ride.getEndTime());
        assertTrue(ride.getEndTime().isBefore(LocalDateTime.now().minusMinutes(30)),
                "the end time is when the bus was last heard from, not 'now'");
        verify(rideRepository).save(ride);
        verify(activeRideCache).remove(1L);
        verify(busSearchCache).clear();
        assertNull(activityTracker.lastSeen(1L), "the ended ride is forgotten");
    }

    @Test
    void leavesARecentlyActiveRideAlone() {
        Ride ride = activeRide(1L, "WB-1");
        when(rideRepository.findByStatus(RideStatus.ACTIVE)).thenReturn(List.of(ride));
        activityTracker.touch(1L, minutesAgo(1));

        assertEquals(0, rideService.endStaleRides());
        assertEquals(RideStatus.ACTIVE, ride.getStatus());
        verify(rideRepository, never()).save(any(Ride.class));
    }

    @Test
    void leavesARideSilentForLessThanTheAutoEndLimit() {
        Ride ride = activeRide(1L, "WB-1");
        when(rideRepository.findByStatus(RideStatus.ACTIVE)).thenReturn(List.of(ride));
        activityTracker.touch(1L, minutesAgo(10));   // hidden from passengers, but not ended yet

        assertEquals(0, rideService.endStaleRides());
        assertEquals(RideStatus.ACTIVE, ride.getStatus());
    }

    @Test
    void anUnknownRideIsSeededFromItsLastStoredLocationAndEndedIfThatIsOld() {
        Ride ride = activeRide(1L, "WB-1");
        when(rideRepository.findByStatus(RideStatus.ACTIVE)).thenReturn(List.of(ride));
        Location old = new Location();
        old.setTimestamp(LocalDateTime.now().minusMinutes(45));
        when(latestLocationResolver.find(1L)).thenReturn(old);

        assertEquals(1, rideService.endStaleRides());
        assertEquals(RideStatus.ENDED, ride.getStatus());
    }

    @Test
    void anUnknownRideWithARecentStoredLocationIsKept() {
        Ride ride = activeRide(1L, "WB-1");
        when(rideRepository.findByStatus(RideStatus.ACTIVE)).thenReturn(List.of(ride));
        Location recent = new Location();
        recent.setTimestamp(LocalDateTime.now().minusMinutes(2));
        when(latestLocationResolver.find(1L)).thenReturn(recent);

        assertEquals(0, rideService.endStaleRides());
        assertEquals(RideStatus.ACTIVE, ride.getStatus());
        assertTrue(activityTracker.isKnown(1L), "its last-seen time is now remembered");
    }

    /* ─── passenger bus-number search ─────────────────────────────── */

    @Test
    void searchHidesRidesThatWentSilent() {
        Ride live = activeRide(10L, "TEST-LIVE");
        Ride silent = activeRide(11L, "TEST-SILENT");
        when(rideRepository.searchByBusNumber(eq(RideStatus.ACTIVE), eq("test"), any(Pageable.class)))
                .thenReturn(List.of(live, silent));
        activityTracker.touch(10L);
        activityTracker.touch(11L, minutesAgo(10));

        List<ActiveRideResponse> found = rideService.searchActiveRidesByBusNumber("TEST");

        assertEquals(1, found.size());
        assertEquals("TEST-LIVE", found.get(0).getBusNumber());
        verify(busSearchCache).put(eq("test"), anyList());
    }

    @Test
    void searchIsAnsweredFromTheCacheWithoutTouchingTheDatabase() {
        ActiveRideResponse cached = new ActiveRideResponse();
        cached.setRideId(5L);
        when(busSearchCache.get("test")).thenReturn(List.of(cached));

        List<ActiveRideResponse> found = rideService.searchActiveRidesByBusNumber("Test");

        assertEquals(1, found.size());
        verifyNoInteractions(rideRepository);
    }

    @Test
    void anEmptyOrPunctuationOnlySearchReturnsNothingAndSkipsTheDatabase() {
        assertTrue(rideService.searchActiveRidesByBusNumber("  --- ").isEmpty());
        assertTrue(rideService.searchActiveRidesByBusNumber(null).isEmpty());
        verifyNoInteractions(rideRepository);
    }
}
