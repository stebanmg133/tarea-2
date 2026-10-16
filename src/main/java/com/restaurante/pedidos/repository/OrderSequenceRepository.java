package com.restaurante.pedidos.repository;

import com.restaurante.pedidos.domain.OrderSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderSequenceRepository extends JpaRepository<OrderSequence, Long> {

    /**
     * Bloquea la fila de la secuencia para leer e incrementar el consecutivo de forma
     * segura ante accesos simultaneos de varios dispositivos.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OrderSequence s where s.id = :id")
    Optional<OrderSequence> findByIdForUpdate(@Param("id") Long id);
}
