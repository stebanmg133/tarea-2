package com.restaurante.pedidos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Secuencia persistente para la numeracion de pedidos.
 *
 * <p>Se guarda en base de datos para que la numeracion continue despues de reiniciar.
 * El servicio que la usa bloquea la fila con {@code SELECT ... FOR UPDATE} dentro de una
 * transaccion, de modo que varios dispositivos no obtengan el mismo numero.</p>
 *
 * <p>Fila unica con {@code id = 1}. No se reutilizan numeros de pedidos cancelados.</p>
 */
@Entity
@Table(name = "order_sequence")
public class OrderSequence {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(name = "last_number", nullable = false)
    private long lastNumber = 0L;

    protected OrderSequence() {
    }

    public OrderSequence(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public long getLastNumber() {
        return lastNumber;
    }

    public void setLastNumber(long lastNumber) {
        this.lastNumber = lastNumber;
    }
}
