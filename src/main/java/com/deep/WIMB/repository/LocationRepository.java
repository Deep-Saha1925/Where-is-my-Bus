/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.repository;

import com.deep.WIMB.model.Location;
import com.deep.WIMB.model.Ride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    /**
     * Deletes up to {@code batchSize} location rows older than {@code cutoff}, never touching rides
     * that are still ACTIVE. Returns how many rows were deleted (less than batchSize means "done").
     *
     * Done in small batches (the caller loops) so a big clean-up never holds one huge transaction
     * or locks the table for long. Each call is its own transaction.
     */
    @Modifying
    @Transactional
    @Query(value = """
            DELETE FROM location
            WHERE id IN (
                SELECT l.id
                FROM location l
                JOIN ride r ON r.id = l.ride_id
                WHERE l.timestamp < :cutoff
                  AND r.status <> 'ACTIVE'
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteOldBatch(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);
}
