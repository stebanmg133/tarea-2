package com.restaurante.pedidos.exception;

/**
 * Error de negocio controlado. Se traduce a HTTP 400 con mensaje legible y sin
 * exponer detalles internos.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
