package com.restaurante.pedidos.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pedido central. Existe un unico pedido (una identidad, un total) aunque sus articulos
 * se distribuyan entre varias areas de preparacion. La distribucion por areas son
 * vistas de trabajo, NO pedidos independientes.
 */
@Entity
@Table(name = "orders",
        uniqueConstraints = @UniqueConstraint(name = "uk_order_code", columnNames = "code"))
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identificador legible y persistente: PED-001, PED-002, ... */
    @Column(nullable = false, length = 20)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDIENTE;

    /** Codigo de mesa (M01..M15) cuando el tipo es MESA. */
    @Column(name = "table_code", length = 10)
    private String tableCode;

    @Column(name = "customer_name", length = 120)
    private String customerName;

    @Column(name = "customer_phone", length = 40)
    private String customerPhone;

    @Column(name = "delivery_address", length = 255)
    private String deliveryAddress;

    /** Valor del domicilio. Solo se aplica y suma en pedidos de tipo DOMICILIO. */
    @Column(name = "delivery_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(name = "general_notes", length = 500)
    private String generalNotes;

    /** Subtotal de articulos activos (sin domicilio). Calculado en el backend. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    /** Total = subtotal + deliveryFee (solo domicilio). Calculado en el backend. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    /**
     * Version del contenido del pedido. Se incrementa al agregar/quitar/modificar
     * articulos. Permite saber si un pedido cambio despues de haberse impreso.
     */
    @Column(name = "content_version", nullable = false)
    private int contentVersion = 1;

    /** Ultima version del contenido que fue enviada a la cola de impresion. */
    @Column(name = "last_printed_version")
    private Integer lastPrintedVersion;

    /** True cuando el pedido cambio despues de la ultima impresion aceptada. */
    @Column(name = "needs_reprint", nullable = false)
    private boolean needsReprint = false;

    /** Origen informativo (dispositivo/usuario) que registro el pedido. */
    @Column(name = "created_by", length = 80)
    private String createdBy;

    /**
     * Token de idempotencia generado por el cliente. Si llega dos veces la misma
     * peticion (doble clic, reintento o reconexion) se devuelve el pedido ya creado
     * en lugar de crear uno duplicado.
     */
    @Column(name = "client_token", length = 80, unique = true)
    private String clientToken;

    @Version
    private Long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC, id ASC")
    private List<OrderEvent> events = new ArrayList<>();

    protected Order() {
    }

    public Order(String code, OrderType type) {
        this.code = code;
        this.type = type;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void addItem(OrderItem item) {
        item.setOrder(this);
        this.items.add(item);
    }

    // --- Getters / Setters -------------------------------------------------

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public OrderType getType() {
        return type;
    }

    public void setType(OrderType type) {
        this.type = type;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getTableCode() {
        return tableCode;
    }

    public void setTableCode(String tableCode) {
        this.tableCode = tableCode;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public BigDecimal getDeliveryFee() {
        return deliveryFee;
    }

    public void setDeliveryFee(BigDecimal deliveryFee) {
        this.deliveryFee = deliveryFee;
    }

    public String getGeneralNotes() {
        return generalNotes;
    }

    public void setGeneralNotes(String generalNotes) {
        this.generalNotes = generalNotes;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public int getContentVersion() {
        return contentVersion;
    }

    public void setContentVersion(int contentVersion) {
        this.contentVersion = contentVersion;
    }

    public Integer getLastPrintedVersion() {
        return lastPrintedVersion;
    }

    public void setLastPrintedVersion(Integer lastPrintedVersion) {
        this.lastPrintedVersion = lastPrintedVersion;
    }

    public boolean isNeedsReprint() {
        return needsReprint;
    }

    public void setNeedsReprint(boolean needsReprint) {
        this.needsReprint = needsReprint;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getClientToken() {
        return clientToken;
    }

    public void setClientToken(String clientToken) {
        this.clientToken = clientToken;
    }

    public Long getVersion() {
        return version;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public List<OrderEvent> getEvents() {
        return events;
    }

    public List<OrderItem> getActiveItems() {
        List<OrderItem> active = new ArrayList<>();
        for (OrderItem i : items) {
            if (!i.isCancelled()) {
                active.add(i);
            }
        }
        return active;
    }
}
