package com.restaurante.pedidos.exception;

/**
 * Recurso no encontrado. Se traduce a HTTP 404.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
