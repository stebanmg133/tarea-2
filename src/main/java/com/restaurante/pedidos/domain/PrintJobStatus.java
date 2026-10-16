package com.restaurante.pedidos.domain;

/**
 * Estados de un trabajo de impresion.
 *
 * <p>Importante: {@link #ACEPTADO} significa que el sistema de impresion (la cola del
 * sistema operativo) acepto el trabajo. NO significa que el papel haya salido
 * fisicamente. Si no es posible confirmarlo, se refleja con
 * {@code physicalConfirmed = false} o con {@link #DESCONOCIDO}.</p>
 */
public enum PrintJobStatus {
    PENDIENTE("Pendiente", "#64748b"),
    ENVIANDO("Enviando", "#d97706"),
    ACEPTADO("Aceptado por la cola de impresión", "#0891b2"),
    ERROR("Error", "#b91c1c"),
    DESCONOCIDO("Estado desconocido", "#7c3aed"),
    REINTENTO_SOLICITADO("Reintento solicitado", "#a16207");

    private final String label;
    private final String color;

    PrintJobStatus(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public String getLabel() {
        return label;
    }

    public String getColor() {
        return color;
    }
}
