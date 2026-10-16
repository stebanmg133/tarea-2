package com.restaurante.pedidos.exception;

/**
 * Falta de autorizacion administrativa. Se traduce a HTTP 401.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
