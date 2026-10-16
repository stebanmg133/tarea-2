package com.restaurante.pedidos.domain;

/**
 * Tipo de pedido. Se diferencian por etiqueta, icono y color en toda la interfaz.
 */
public enum OrderType {
    /** Ocupa una mesa (M01..M15). */
    MESA("Mesa", "icono-mesa", "#c2410c"),
    /** Requiere datos del cliente, direccion y valor de domicilio. */
    DOMICILIO("Domicilio", "icono-moto", "#1d4ed8"),
    /** Para llevar: no ocupa mesa ni cobra domicilio. */
    LLEVAR("Para llevar", "icono-bolsa", "#15803d");

    private final String label;
    private final String icon;
    private final String color;

    OrderType(String label, String icon, String color) {
        this.label = label;
        this.icon = icon;
        this.color = color;
    }

    public String getLabel() {
        return label;
    }

    public String getIcon() {
        return icon;
    }

    public String getColor() {
        return color;
    }
}
