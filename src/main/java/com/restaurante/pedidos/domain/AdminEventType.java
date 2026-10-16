package com.restaurante.pedidos.domain;

/**
 * Tipos de evento administrativo y del modulo de bloqueo.
 */
public enum AdminEventType {
    ADMIN_LOGIN_OK("Inicio de sesión administrativo"),
    ADMIN_LOGIN_FAIL("Intento de inicio de sesión fallido"),
    ADMIN_LOGOUT("Cierre de sesión administrativo"),
    PERIODO_RENOVADO("Período de validación renovado"),
    PASSWORD_CAMBIADA("Contraseña principal cambiada"),
    TEMP_PASSWORD_CREADA("Contraseña temporal creada"),
    TEMP_PASSWORD_ELIMINADA("Contraseña temporal eliminada"),
    INTERVALO_CAMBIADO("Intervalo de validación cambiado"),
    MODULO_ACTIVADO("Módulo de bloqueo activado"),
    MODULO_DESACTIVADO("Módulo de bloqueo desactivado"),
    EMAIL_CAMBIADO("Correo administrativo cambiado"),
    SISTEMA_BLOQUEADO("Sistema bloqueado por vencimiento"),
    SISTEMA_DESBLOQUEADO("Sistema desbloqueado"),
    DESBLOQUEO_FALLIDO("Intento de desbloqueo fallido"),
    RECUPERACION_EJECUTADA("Procedimiento de recuperación ejecutado"),
    BACKUP_CREADO("Copia de seguridad creada"),
    BACKUP_ERROR("Error al crear copia de seguridad"),
    AJUSTE_CONFIANZA("Ajuste de parámetros del sistema");

    private final String label;

    AdminEventType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
