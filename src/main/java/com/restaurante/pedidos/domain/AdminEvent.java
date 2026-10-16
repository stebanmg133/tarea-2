package com.restaurante.pedidos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Historial de eventos administrativos y del modulo de bloqueo.
 * Nunca guarda contrasenas ni secretos: solo descripciones y resultados.
 */
@Entity
@Table(name = "admin_event")
public class AdminEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AdminEventType type;

    @Column(nullable = false, length = 400)
    private String description;

    @Column(length = 80)
    private String actor;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected AdminEvent() {
    }

    public AdminEvent(AdminEventType type, String description, String actor) {
        this.type = type;
        this.description = description;
        this.actor = actor;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public AdminEventType getType() {
        return type;
    }

    public void setType(AdminEventType type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
