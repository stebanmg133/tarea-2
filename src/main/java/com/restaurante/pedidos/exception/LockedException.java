package com.restaurante.pedidos.exception;

/**
 * El sistema esta bloqueado por el modulo de bloqueo temporal. Se traduce a HTTP 423.
 * Evita que se creen o modifiquen pedidos mediante llamadas directas a la API.
 */
public class LockedException extends RuntimeException {

    public LockedException(String message) {
        super(message);
    }
}
