package com.deep.WIMB.repository;

import com.deep.WIMB.model.Location;
import com.deep.WIMB.model.Ride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {

    // Explicit JPQL rather than the derived-query naming convention
    // (findTopByRideIdOrderByTimestampDesc) on purpose. That name used to
    // work by Spring Data silently walking the `ride` relationship (no
    // `rideId` field existed on Location, so it parsed "RideId" as
    // Ride -> id). The moment Location gained a real field literally named
    // `rideId` (added for JSON/WebSocket payloads -- see Location.java),
    // Spring Data matched that field directly instead, and broke this
    // query outright since that field is @Transient (UnknownPathException:
    // Could not resolve attribute 'rideId'). Being explicit here means this
    // can never silently break again just because Location's fields change.
    // LIMIT 1 is required here -- without it, this throws
    // NonUniqueResultException the moment a ride has 2+ location rows
    // (completely normal: e.g. locally, with Redis unreachable, every GPS
    // tick falls back to a direct Postgres save, so rows pile up fast).
    // The old findTopByRideId... derived-query name got LIMIT 1 for free
    // from Spring Data's "Top" keyword; a hand-written @Query doesn't get
    // that automatically and needs it spelled out.
    @Query("SELECT l FROM Location l WHERE l.ride.id = :rideId ORDER BY l.timestamp DESC LIMIT 1")
    Optional<Location> findTopByRideIdOrderByTimestampDesc(@Param("rideId") Long rideId);
}
