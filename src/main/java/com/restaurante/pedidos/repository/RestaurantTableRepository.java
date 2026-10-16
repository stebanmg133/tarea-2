package com.restaurante.pedidos.repository;

import com.restaurante.pedidos.domain.RestaurantTable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, Long> {

    Optional<RestaurantTable> findByCode(String code);

    List<RestaurantTable> findAllByOrderByCodeAsc();

    /**
     * Bloqueo pesimista de la fila de la mesa para evitar que dos dispositivos
     * abran a la vez dos pedidos principales para la misma mesa.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RestaurantTable t where t.id = :id")
    Optional<RestaurantTable> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RestaurantTable t where t.code = :code")
    Optional<RestaurantTable> findByCodeForUpdate(@Param("code") String code);

    long countByOccupiedTrue();
}
