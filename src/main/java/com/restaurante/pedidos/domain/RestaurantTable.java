package com.restaurante.pedidos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;

/**
 * Mesa del restaurante (M01..M15).
 *
 * <p>{@code occupied} indica si la mesa tiene un pedido activo. El campo {@code version}
 * permite bloqueo optimista para evitar que dos dispositivos ocupen la misma mesa.</p>
 */
@Entity
@Table(name = "restaurant_table",
        uniqueConstraints = @UniqueConstraint(name = "uk_table_code", columnNames = "code"))
public class RestaurantTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 10)
    private String code;

    @Column(nullable = false)
    private boolean occupied = false;

    /** Codigo (PED-###) del pedido principal activo, si la mesa esta ocupada. */
    @Column(name = "current_order_code", length = 20)
    private String currentOrderCode;

    @Version
    private Long version;

    protected RestaurantTable() {
    }

    public RestaurantTable(String code) {
        this.code = code;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public boolean isOccupied() {
        return occupied;
    }

    public void setOccupied(boolean occupied) {
        this.occupied = occupied;
    }

    public String getCurrentOrderCode() {
        return currentOrderCode;
    }

    public void setCurrentOrderCode(String currentOrderCode) {
        this.currentOrderCode = currentOrderCode;
    }

    public Long getVersion() {
        return version;
    }
}
