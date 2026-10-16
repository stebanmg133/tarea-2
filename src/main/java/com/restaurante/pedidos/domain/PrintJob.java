package com.restaurante.pedidos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Trabajo de impresion. Cada intento tiene su propio registro.
 *
 * <p>{@code area} nulo significa "pedido completo"; con area significa "solo el area".
 * {@code dedupeKey} evita que un doble clic genere trabajos identicos duplicados.
 * {@code sourceVersion} guarda la version del contenido del pedido que se envio.</p>
 */
@Entity
@Table(name = "print_job")
public class PrintJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** Area destino; null = pedido completo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id")
    private PreparationArea area;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PrintJobStatus status = PrintJobStatus.PENDIENTE;

    /** Numero de intento dentro del pedido/area. */
    @Column(nullable = false)
    private int attempt = 1;

    /** Version del contenido del pedido que representa este trabajo. */
    @Column(name = "source_version", nullable = false)
    private int sourceVersion = 1;

    /** Verdadero si es una reimpresion de un trabajo ya aceptado. */
    @Column(name = "reprint", nullable = false)
    private boolean reprint = false;

    /** Clave de deduplicacion (pedido + area + version + ventana de tiempo). */
    @Column(name = "dedupe_key", length = 120)
    private String dedupeKey;

    /** Contenido exacto enviado (texto de la comanda). */
    @Column(columnDefinition = "CLOB")
    private String content;

    @Column(name = "printer_name", length = 160)
    private String printerName;

    /** true solo si el sistema confirma que se acepto en la cola. */
    @Column(nullable = false)
    private boolean simulated = false;

    /**
     * true unicamente si se pudo confirmar que el papel salio fisicamente.
     * Normalmente false: la cola acepta el trabajo, pero no confirma la impresion fisica.
     */
    @Column(name = "physical_confirmed", nullable = false)
    private boolean physicalConfirmed = false;

    @Column(length = 500)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected PrintJob() {
    }

    public PrintJob(Order order, PreparationArea area, int sourceVersion) {
        this.order = order;
        this.area = area;
        this.sourceVersion = sourceVersion;
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public PreparationArea getArea() {
        return area;
    }

    public void setArea(PreparationArea area) {
        this.area = area;
    }

    public PrintJobStatus getStatus() {
        return status;
    }

    public void setStatus(PrintJobStatus status) {
        this.status = status;
    }

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    public int getSourceVersion() {
        return sourceVersion;
    }

    public void setSourceVersion(int sourceVersion) {
        this.sourceVersion = sourceVersion;
    }

    public boolean isReprint() {
        return reprint;
    }

    public void setReprint(boolean reprint) {
        this.reprint = reprint;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public void setDedupeKey(String dedupeKey) {
        this.dedupeKey = dedupeKey;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getPrinterName() {
        return printerName;
    }

    public void setPrinterName(String printerName) {
        this.printerName = printerName;
    }

    public boolean isSimulated() {
        return simulated;
    }

    public void setSimulated(boolean simulated) {
        this.simulated = simulated;
    }

    public boolean isPhysicalConfirmed() {
        return physicalConfirmed;
    }

    public void setPhysicalConfirmed(boolean physicalConfirmed) {
        this.physicalConfirmed = physicalConfirmed;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
