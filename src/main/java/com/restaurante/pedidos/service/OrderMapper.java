package com.restaurante.pedidos.service;

import com.restaurante.pedidos.domain.Order;
import com.restaurante.pedidos.domain.OrderEvent;
import com.restaurante.pedidos.domain.OrderItem;
import com.restaurante.pedidos.domain.OrderItemAreaTask;
import com.restaurante.pedidos.domain.OrderStatus;
import com.restaurante.pedidos.dto.OrderDtos;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Convierte entidades en DTOs. Debe invocarse dentro de la transaccion que carga las
 * entidades, ya que inicializa colecciones perezosas.
 */
@Component
public class OrderMapper {

    public OrderDtos.OrderView toView(Order order) {
        List<OrderDtos.ItemView> items = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            items.add(toItemView(item));
        }
        return new OrderDtos.OrderView(
                order.getId(),
                order.getCode(),
                order.getType().name(),
                order.getType().getLabel(),
                order.getType().getColor(),
                order.getStatus().name(),
                order.getStatus().getLabel(),
                order.getStatus().getColor(),
                order.getTableCode(),
                order.getCustomerName(),
                order.getCustomerPhone(),
                order.getDeliveryAddress(),
                nz(order.getDeliveryFee()),
                order.getGeneralNotes(),
                nz(order.getSubtotal()),
                nz(order.getTotal()),
                str(order.getCreatedAt()),
                str(order.getUpdatedAt()),
                str(order.getDeliveredAt()),
                str(order.getCancelledAt()),
                order.getCancelReason(),
                order.isNeedsReprint(),
                order.getContentVersion(),
                order.getLastPrintedVersion(),
                order.getStatus().isActive(),
                order.getLastPrintedVersion() != null,
                items
        );
    }

    public OrderDtos.ItemView toItemView(OrderItem item) {
        List<String> areaNames = new ArrayList<>();
        List<OrderDtos.AreaTaskView> tasks = new ArrayList<>();
        for (OrderItemAreaTask task : item.getAreaTasks()) {
            areaNames.add(task.getArea().getName());
            tasks.add(new OrderDtos.AreaTaskView(
                    task.getArea().getId(),
                    task.getArea().getCode(),
                    task.getArea().getName(),
                    task.getStatus().name(),
                    task.getStatus().getLabel()
            ));
        }
        return new OrderDtos.ItemView(
                item.getId(),
                item.getProduct() != null ? item.getProduct().getId() : null,
                item.getProductName(),
                item.getCategoryName(),
                nz(item.getUnitPrice()),
                item.getQuantity(),
                item.getFlavorName(),
                item.getVariantName(),
                item.getNotes(),
                item.getStatus().name(),
                item.getStatus().getLabel(),
                item.isCancelled(),
                nz(item.lineTotal()),
                areaNames,
                tasks
        );
    }

    public OrderDtos.OrderSummaryView toSummary(Order order) {
        int itemCount = 0;
        for (OrderItem item : order.getItems()) {
            if (!item.isCancelled()) {
                itemCount += item.getQuantity();
            }
        }
        return new OrderDtos.OrderSummaryView(
                order.getId(),
                order.getCode(),
                order.getType().name(),
                order.getType().getLabel(),
                order.getType().getColor(),
                order.getStatus().name(),
                order.getStatus().getLabel(),
                order.getStatus().getColor(),
                order.getTableCode(),
                order.getCustomerName(),
                order.getDeliveryAddress(),
                nz(order.getTotal()),
                str(order.getCreatedAt()),
                str(order.getDeliveredAt()),
                order.isNeedsReprint(),
                itemCount
        );
    }

    public OrderDtos.EventView toEventView(OrderEvent event) {
        return new OrderDtos.EventView(
                event.getId(),
                event.getType().name(),
                event.getType().getLabel(),
                event.getDescription(),
                event.getActor(),
                str(event.getCreatedAt())
        );
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String str(java.time.LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    /** Utilidad para etiquetas de estado fuera del contexto de entidades. */
    public static String label(OrderStatus status) {
        return status.getLabel();
    }
}
