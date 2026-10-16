package com.restaurante.pedidos.domain;

/**
 * Categorias rapidas para tomar pedidos en pantalla tactil.
 * Se usan como filtro rapido en la toma de pedidos.
 */
public enum QuickCategory {
    COMIDAS("Comidas"),
    BEBIDAS("Bebidas"),
    PIZZAS("Pizzas"),
    GASEOSAS("Gaseosas");

    private final String label;

    QuickCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
