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
    @Query("SELECT l FROM Location l WHERE l.ride.id = :rideId ORDER BY l.timestamp DESC")
    Optional<Location> findTopByRideIdOrderByTimestampDesc(@Param("rideId") Long rideId);
}
