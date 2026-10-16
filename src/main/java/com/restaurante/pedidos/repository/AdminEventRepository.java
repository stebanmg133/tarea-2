package com.restaurante.pedidos.repository;

import com.restaurante.pedidos.domain.AdminEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminEventRepository extends JpaRepository<AdminEvent, Long> {

    List<AdminEvent> findTop200ByOrderByCreatedAtDesc();
}
