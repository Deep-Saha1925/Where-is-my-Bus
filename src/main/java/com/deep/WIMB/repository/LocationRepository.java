package com.deep.WIMB.repository;

import com.deep.WIMB.model.Location;
import com.deep.WIMB.model.Ride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {

    Optional<Location> findTopByRideIdOrderByTimestampDesc(Long rideId);
}
