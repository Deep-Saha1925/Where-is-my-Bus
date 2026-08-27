package com.deep.WIMB.repository;

import com.deep.WIMB.model.Route;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RouteRepository extends JpaRepository<Route, Long> {

    Optional<Route> findByRouteCode(String routeCode);
    boolean existsByRouteCode(String routeCode);
    List<Route> findAllByOrderByRouteNameAsc();
}