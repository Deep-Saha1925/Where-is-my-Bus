package com.deep.WIMB.repository;

import com.deep.WIMB.model.Route;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RouteRepository extends JpaRepository<Route, Long> {
    boolean existsByRouteCode(String routeCode);
    Optional<Route> findByRouteCode(String routeCode);
}