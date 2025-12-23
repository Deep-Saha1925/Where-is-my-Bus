package com.deep.WIMB.repository;

import com.deep.WIMB.enums.RideStatus;
import com.deep.WIMB.model.Bus;
import com.deep.WIMB.model.Ride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RideRepository extends JpaRepository<Ride, Long> {

    Optional<Ride> findByBusAndStatus(Bus bus, RideStatus status);

    List<Ride> findBySourceAndDestinationAndStatus(
            String source,
            String destination,
            RideStatus status
    );
}
