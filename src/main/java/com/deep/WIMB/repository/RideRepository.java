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
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RideRepository extends JpaRepository<Ride, Long> {

    Optional<Ride> findByBusAndStatus(Bus bus, RideStatus status);

    List<Ride> findByStatus(RideStatus status);

    /**
     * Passenger "search by bus number": only rides with the given status whose bus number
     * contains {@code query}, filtered in the database instead of loading every active ride
     * and filtering in the browser.
     *
     * {@code query} must already be normalised (lower-case letters and digits only, see
     * RideService#normalizeBusNumber). The bus number column is normalised the same way in
     * SQL, so "wb23a1245" matches "WB-23A-1245". join fetch loads the bus in the same query
     * (no extra query per ride) and the Pageable caps how many rows come back.
     */
    @Query("""
            select r from Ride r
            join fetch r.bus b
            where r.status = :status
              and replace(replace(replace(replace(replace(lower(b.busNumber), '-', ''), ' ', ''), '_', ''), '/', ''), '.', '')
                  like concat('%', :query, '%')
            order by b.busNumber
            """)
    List<Ride> searchByBusNumber(@Param("status") RideStatus status,
                                 @Param("query") String query,
                                 Pageable pageable);
}
