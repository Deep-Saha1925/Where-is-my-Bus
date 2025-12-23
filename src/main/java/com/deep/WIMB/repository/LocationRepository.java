package com.deep.WIMB.repository;

import com.deep.WIMB.model.Location;
import com.deep.WIMB.model.Ride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    Optional<Location> findTopByRideOrderByTimestampDesc(Ride ride);
}
