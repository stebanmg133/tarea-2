package com.restaurante.pedidos.repository;

import com.restaurante.pedidos.domain.OrderItemAreaTask;
import com.restaurante.pedidos.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderItemAreaTaskRepository extends JpaRepository<OrderItemAreaTask, Long> {

    /**
     * Tareas de un area, para pedidos activos, que aun no estan listas ni entregadas.
     * Es la vista activa de preparacion del area.
     */
    @Query("select t from OrderItemAreaTask t "
            + "join fetch t.orderItem i join fetch i.order o "
            + "where t.area.id = :areaId "
            + "and o.status in :activeStatuses "
            + "and i.cancelled = false "
            + "and t.status in :workStatuses "
            + "order by o.createdAt asc, i.id asc")
    List<OrderItemAreaTask> findActiveWorkByArea(@Param("areaId") Long areaId,
                                                 @Param("activeStatuses") List<OrderStatus> activeStatuses,
                                                 @Param("workStatuses") List<OrderStatus> workStatuses);

    @Query("select t from OrderItemAreaTask t "
            + "join fetch t.orderItem i join fetch i.order o "
            + "where o.id = :orderId "
            + "order by t.area.sortOrder asc")
    List<OrderItemAreaTask> findByOrderId(@Param("orderId") Long orderId);
}
