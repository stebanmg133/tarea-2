package com.restaurante.pedidos.repository;

import com.restaurante.pedidos.domain.OrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {

    List<OrderEvent> findByOrderIdOrderByCreatedAtAsc(Long orderId);
}
