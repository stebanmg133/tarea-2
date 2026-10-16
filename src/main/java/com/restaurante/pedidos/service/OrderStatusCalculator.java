package com.restaurante.pedidos.service;

import com.restaurante.pedidos.domain.Order;
import com.restaurante.pedidos.domain.OrderItem;
import com.restaurante.pedidos.domain.OrderItemAreaTask;
import com.restaurante.pedidos.domain.OrderStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reglas consistentes para derivar:
 * <ul>
 *   <li>el estado de un articulo a partir del estado de sus tareas por area;</li>
 *   <li>el estado global del pedido a partir del estado de sus articulos activos.</li>
 * </ul>
 *
 * <p>Reglas del articulo (ignorando tareas canceladas):</p>
 * <ol>
 *   <li>Si el articulo esta cancelado -> CANCELADO.</li>
 *   <li>Todas las tareas ENTREGADO -> ENTREGADO.</li>
 *   <li>Todas las tareas LISTO o ENTREGADO (y al menos una LISTO) -> LISTO.</li>
 *   <li>Al menos una tarea EN_PREPARACION o superior -> EN_PREPARACION.</li>
 *   <li>En otro caso -> PENDIENTE.</li>
 * </ol>
 *
 * <p>Reglas del pedido (sobre articulos activos, es decir no cancelados):</p>
 * <ol>
 *   <li>Si el pedido fue cancelado explicitamente -> CANCELADO.</li>
 *   <li>Sin articulos activos -> CANCELADO (no queda trabajo que entregar).</li>
 *   <li>Todos los articulos activos ENTREGADO -> ENTREGADO.</li>
 *   <li>Todos los articulos activos LISTO o ENTREGADO, con al menos uno LISTO -> LISTO.</li>
 *   <li>Algun articulo EN_PREPARACION, LISTO o ENTREGADO -> EN_PREPARACION.</li>
 *   <li>En otro caso -> PENDIENTE.</li>
 * </ol>
 */
@Component
public class OrderStatusCalculator {

    /** Calcula el estado de un articulo a partir de sus tareas de area. */
    public OrderStatus calculateItemStatus(OrderItem item) {
        if (item.isCancelled()) {
            return OrderStatus.CANCELADO;
        }
        List<OrderItemAreaTask> tasks = item.getAreaTasks();
        if (tasks == null || tasks.isEmpty()) {
            return OrderStatus.PENDIENTE;
        }
        boolean allDelivered = true;
        boolean allReadyOrDelivered = true;
        boolean atLeastOneReady = false;
        boolean anyInPrepOrBeyond = false;

        for (OrderItemAreaTask task : tasks) {
            OrderStatus s = task.getStatus();
            if (s == OrderStatus.CANCELADO) {
                continue;
            }
            if (s != OrderStatus.ENTREGADO) {
                allDelivered = false;
            }
            if (s != OrderStatus.LISTO && s != OrderStatus.ENTREGADO) {
                allReadyOrDelivered = false;
            }
            if (s == OrderStatus.LISTO || s == OrderStatus.ENTREGADO) {
                atLeastOneReady = true;
            }
            if (s == OrderStatus.EN_PREPARACION || s == OrderStatus.LISTO || s == OrderStatus.ENTREGADO) {
                anyInPrepOrBeyond = true;
            }
        }

        // Si todas las tareas estan canceladas, el articulo queda entregado/neutral.
        boolean anyNonCancelled = tasks.stream().anyMatch(t -> t.getStatus() != OrderStatus.CANCELADO);
        if (!anyNonCancelled) {
            return OrderStatus.ENTREGADO;
        }
        if (allDelivered) {
            return OrderStatus.ENTREGADO;
        }
        if (allReadyOrDelivered && atLeastOneReady) {
            return OrderStatus.LISTO;
        }
        if (anyInPrepOrBeyond) {
            return OrderStatus.EN_PREPARACION;
        }
        return OrderStatus.PENDIENTE;
    }

    /** Recalcula y fija el estado de un articulo. Devuelve el estado resultante. */
    public OrderStatus refreshItemStatus(OrderItem item) {
        OrderStatus status = calculateItemStatus(item);
        item.setStatus(status);
        return status;
    }

    /** Recalcula y fija el estado global del pedido. Devuelve el estado resultante. */
    public OrderStatus refreshOrderStatus(Order order) {
        OrderStatus status = calculateOrderStatus(order);
        order.setStatus(status);
        return status;
    }

    /** Calcula el estado global del pedido a partir de sus articulos activos. */
    public OrderStatus calculateOrderStatus(Order order) {
        if (order.getStatus() == OrderStatus.CANCELADO) {
            return OrderStatus.CANCELADO;
        }
        List<OrderItem> items = order.getItems();
        if (items == null || items.isEmpty()) {
            return OrderStatus.PENDIENTE;
        }
        List<OrderItem> active = order.getActiveItems();
        if (active.isEmpty()) {
            return OrderStatus.CANCELADO;
        }
        boolean allDelivered = true;
        boolean allReadyOrDelivered = true;
        boolean atLeastOneReady = false;
        boolean anyInPrepOrBeyond = false;
        for (OrderItem item : active) {
            OrderStatus s = item.getStatus();
            if (s != OrderStatus.ENTREGADO) {
                allDelivered = false;
            }
            if (s != OrderStatus.LISTO && s != OrderStatus.ENTREGADO) {
                allReadyOrDelivered = false;
            }
            if (s == OrderStatus.LISTO || s == OrderStatus.ENTREGADO) {
                atLeastOneReady = true;
            }
            if (s == OrderStatus.EN_PREPARACION || s == OrderStatus.LISTO || s == OrderStatus.ENTREGADO) {
                anyInPrepOrBeyond = true;
            }
        }
        if (allDelivered) {
            return OrderStatus.ENTREGADO;
        }
        if (allReadyOrDelivered && atLeastOneReady) {
            return OrderStatus.LISTO;
        }
        if (anyInPrepOrBeyond) {
            return OrderStatus.EN_PREPARACION;
        }
        return OrderStatus.PENDIENTE;
    }
}
