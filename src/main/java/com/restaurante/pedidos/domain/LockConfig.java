package com.restaurante.pedidos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Configuracion del modulo de bloqueo temporal ("hombre muerto").
 *
 * <p>Fila unica ({@code id = 1}). Guarda el estado del modulo, el intervalo, la fecha de
 * proxima validacion y los hashes de las contrasenas. Persiste entre reinicios y
 * actualizaciones del navegador: la decision de bloquear se toma en el servidor.</p>
 *
 * <p>Las contrasenas se guardan SIEMPRE con hash BCrypt; nunca en texto plano.</p>
 */
@Entity
@Table(name = "lock_config")
public class LockConfig {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(name = "module_enabled", nullable = false)
    private boolean moduleEnabled = true;

    /** Intervalo de validacion en meses. Inicial: 2. */
    @Column(name = "interval_months", nullable = false)
    private int intervalMonths = 2;

    @Column(name = "last_validation_at")
    private LocalDateTime lastValidationAt;

    /** Fecha en que vence el periodo y el sistema se bloquea si no se valida. */
    @Column(name = "next_due_at")
    private LocalDateTime nextDueAt;

    @Column(name = "admin_email", length = 160)
    private String adminEmail;

    /** Hash BCrypt de la contrasena principal. */
    @Column(name = "main_password_hash", length = 120)
    private String mainPasswordHash;

    /** Hash BCrypt de la contrasena temporal de desbloqueo (opcional). */
    @Column(name = "temp_password_hash", length = 120)
    private String tempPasswordHash;

    @Column(name = "temp_password_expires_at")
    private LocalDateTime tempPasswordExpiresAt;

    /** true cuando la credencial aun es la inicial y conviene cambiarla. */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public boolean isModuleEnabled() {
        return moduleEnabled;
    }

    public void setModuleEnabled(boolean moduleEnabled) {
        this.moduleEnabled = moduleEnabled;
    }

    public int getIntervalMonths() {
        return intervalMonths;
    }

    public void setIntervalMonths(int intervalMonths) {
        this.intervalMonths = intervalMonths;
    }

    public LocalDateTime getLastValidationAt() {
        return lastValidationAt;
    }

    public void setLastValidationAt(LocalDateTime lastValidationAt) {
        this.lastValidationAt = lastValidationAt;
    }

    public LocalDateTime getNextDueAt() {
        return nextDueAt;
    }

    public void setNextDueAt(LocalDateTime nextDueAt) {
        this.nextDueAt = nextDueAt;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public String getMainPasswordHash() {
        return mainPasswordHash;
    }

    public void setMainPasswordHash(String mainPasswordHash) {
        this.mainPasswordHash = mainPasswordHash;
    }

    public String getTempPasswordHash() {
        return tempPasswordHash;
    }

    public void setTempPasswordHash(String tempPasswordHash) {
        this.tempPasswordHash = tempPasswordHash;
    }

    public LocalDateTime getTempPasswordExpiresAt() {
        return tempPasswordExpiresAt;
    }

    public void setTempPasswordExpiresAt(LocalDateTime tempPasswordExpiresAt) {
        this.tempPasswordExpiresAt = tempPasswordExpiresAt;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
