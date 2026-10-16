package com.restaurante.pedidos.domain;

/**
 * Tipos de evento registrados en el historial de un pedido.
 * Sirve para saber que cambio, cuando y quien lo hizo.
 */
public enum OrderEventType {
    PEDIDO_CREADO("Pedido creado"),
    ARTICULO_AGREGADO("Artículo agregado"),
    ARTICULO_ELIMINADO("Artículo eliminado"),
    ARTICULO_ACTUALIZADO("Artículo actualizado"),
    ESTADO_ARTICULO_ACTUALIZADO("Estado de artículo actualizado"),
    ESTADO_PEDIDO_ACTUALIZADO("Estado del pedido actualizado"),
    PEDIDO_ENTREGADO("Pedido entregado"),
    PEDIDO_CANCELADO("Pedido cancelado"),
    MESA_OCUPADA("Mesa ocupada"),
    MESA_LIBERADA("Mesa liberada"),
    IMPRESION_SOLICITADA("Impresión solicitada"),
    IMPRESION_REINTENTO("Reintento de impresión"),
    PEDIDO_MODIFICADO("Pedido modificado");

    private final String label;

    OrderEventType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
