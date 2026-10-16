package com.restaurante.pedidos.repository;

import com.restaurante.pedidos.domain.PreparationArea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PreparationAreaRepository extends JpaRepository<PreparationArea, Long> {

    Optional<PreparationArea> findByCode(String code);

    List<PreparationArea> findAllByOrderBySortOrderAscIdAsc();

    List<PreparationArea> findByActiveTrueOrderBySortOrderAscIdAsc();

    long countByActiveTrue();
}
