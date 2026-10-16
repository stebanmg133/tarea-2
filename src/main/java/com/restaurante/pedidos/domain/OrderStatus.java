package com.restaurante.pedidos.domain;

/**
 * Estados principales de un pedido y de un articulo.
 *
 * <p>El estado global del pedido se calcula a partir del estado de sus articulos
 * activos (ver {@code OrderStatusCalculator}), nunca de forma manual desde el cliente.</p>
 */
public enum OrderStatus {
    PENDIENTE("Pendiente", "#64748b"),
    EN_PREPARACION("En preparación", "#d97706"),
    LISTO("Listo", "#0891b2"),
    ENTREGADO("Entregado", "#15803d"),
    CANCELADO("Cancelado", "#b91c1c");

    private final String label;
    private final String color;

    OrderStatus(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public String getLabel() {
        return label;
    }

    public String getColor() {
        return color;
    }

    public boolean isActive() {
        return this == PENDIENTE || this == EN_PREPARACION || this == LISTO;
    }
}
