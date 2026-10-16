package com.restaurante.pedidos.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTOs relacionados con pedidos y articulos.
 */
public final class OrderDtos {

    private OrderDtos() {
    }

    /** Estado de una tarea de articulo por area. */
    public record AreaTaskView(
            Long areaId,
            String areaCode,
            String areaName,
            String status,
            String statusLabel
    ) {
    }

    /** Vista de un articulo del pedido. */
    public record ItemView(
            Long id,
            Long productId,
            String productName,
            String categoryName,
            BigDecimal unitPrice,
            int quantity,
            String flavorName,
            String variantName,
            String notes,
            String status,
            String statusLabel,
            boolean cancelled,
            BigDecimal lineTotal,
            List<String> areaNames,
            List<AreaTaskView> tasks
    ) {
    }

    /** Vista completa de un pedido. */
    public record OrderView(
            Long id,
            String code,
            String type,
            String typeLabel,
            String typeColor,
            String status,
            String statusLabel,
            String statusColor,
            String tableCode,
            String customerName,
            String customerPhone,
            String deliveryAddress,
            BigDecimal deliveryFee,
            String generalNotes,
            BigDecimal subtotal,
            BigDecimal total,
            String createdAt,
            String updatedAt,
            String deliveredAt,
            String cancelledAt,
            String cancelReason,
            boolean needsReprint,
            int contentVersion,
            Integer lastPrintedVersion,
            boolean active,
            boolean printed,
            List<ItemView> items
    ) {
    }

    /** Vista compacta para listados. */
    public record OrderSummaryView(
            Long id,
            String code,
            String type,
            String typeLabel,
            String typeColor,
            String status,
            String statusLabel,
            String statusColor,
            String tableCode,
            String customerName,
            String deliveryAddress,
            BigDecimal total,
            String createdAt,
            String deliveredAt,
            boolean needsReprint,
            int itemCount
    ) {
    }

    public record CreateItemRequest(
            Long productId,
            Integer quantity,
            String flavor,
            String variant,
            String notes
    ) {
    }

    public record CreateOrderRequest(
            String type,
            String tableCode,
            String customerName,
            String customerPhone,
            String deliveryAddress,
            BigDecimal deliveryFee,
            String generalNotes,
            List<CreateItemRequest> items,
            /** Token de idempotencia generado por el cliente para evitar duplicados. */
            String clientToken
    ) {
    }

    public record UpdateOrderRequest(
            String customerName,
            String customerPhone,
            String deliveryAddress,
            BigDecimal deliveryFee,
            String generalNotes
    ) {
    }

    public record ItemStatusRequest(
            String status,
            Long areaId
    ) {
    }

    public record CancelRequest(
            String reason
    ) {
    }

    public record EventView(
            Long id,
            String type,
            String typeLabel,
            String description,
            String actor,
            String createdAt
    ) {
    }

    public record OrderDetailView(
            OrderView order,
            List<EventView> events
    ) {
    }
}
