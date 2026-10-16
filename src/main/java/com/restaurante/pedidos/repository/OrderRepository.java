package com.restaurante.pedidos.repository;

import com.restaurante.pedidos.domain.Order;
import com.restaurante.pedidos.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByCode(String code);

    boolean existsByCode(String code);

    Optional<Order> findByClientToken(String clientToken);

    List<Order> findByStatusInOrderByCreatedAtAsc(List<OrderStatus> statuses);

    long countByStatus(OrderStatus status);

    long countByStatusIn(List<OrderStatus> statuses);

    List<Order> findByTableCodeAndStatusIn(String tableCode, List<OrderStatus> statuses);

    long countByTypeAndStatusIn(com.restaurante.pedidos.domain.OrderType type, List<OrderStatus> statuses);

    List<Order> findByNeedsReprintTrueAndStatusIn(List<OrderStatus> statuses);

    List<Order> findByCreatedAtBetweenAndStatusNot(LocalDateTime from, LocalDateTime to, OrderStatus excluded);

    @Query("select coalesce(sum(o.total), 0) from Order o "
            + "where o.status = com.restaurante.pedidos.domain.OrderStatus.ENTREGADO "
            + "and o.deliveredAt between :from and :to")
    java.math.BigDecimal sumDeliveredTotalBetween(@Param("from") LocalDateTime from,
                                                  @Param("to") LocalDateTime to);

    long countByStatusAndDeliveredAtBetween(OrderStatus status, LocalDateTime from, LocalDateTime to);

    long countByStatusAndCancelledAtBetween(OrderStatus status, LocalDateTime from, LocalDateTime to);

    List<Order> findTop50ByOrderByCreatedAtDesc();
}
