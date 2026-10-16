package com.restaurante.pedidos.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Producto del catalogo.
 *
 * <p>Puede tener sabores (obligatorios o no) y variantes/tamanos con precios propios.
 * Un producto puede enviarse a una o varias {@link PreparationArea} (destinos).</p>
 *
 * <p>Un producto inactivo no se ofrece al tomar pedidos nuevos, pero sigue visible en
 * los pedidos historicos donde ya fue utilizado (los articulos guardan su propio
 * nombre y precio).</p>
 */
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    /** Precio base. Se usa cuando el producto no tiene variantes ni sabores con recargo. */
    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    @Column(name = "base_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal basePrice = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    /** Si es true, el cliente DEBE elegir un sabor al agregar el producto. */
    @Column(name = "requires_flavor", nullable = false)
    private boolean requiresFlavor = false;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(length = 255)
    private String description;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    private List<Flavor> flavors = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ProductVariant> variants = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "product_area",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "area_id"))
    private Set<PreparationArea> areas = new LinkedHashSet<>();

    public Product() {
    }

    public Product(String name, Category category, BigDecimal basePrice) {
        this.name = name;
        this.category = category;
        this.basePrice = basePrice;
    }

    public void addFlavor(Flavor flavor) {
        flavor.setProduct(this);
        this.flavors.add(flavor);
    }

    public void addVariant(ProductVariant variant) {
        variant.setProduct(this);
        this.variants.add(variant);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isRequiresFlavor() {
        return requiresFlavor;
    }

    public void setRequiresFlavor(boolean requiresFlavor) {
        this.requiresFlavor = requiresFlavor;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Flavor> getFlavors() {
        return flavors;
    }

    public List<ProductVariant> getVariants() {
        return variants;
    }

    public Set<PreparationArea> getAreas() {
        return areas;
    }

    public void setAreas(Set<PreparationArea> areas) {
        this.areas = areas;
    }

    public boolean hasVariants() {
        return variants != null && !variants.isEmpty();
    }

    public boolean hasFlavors() {
        return flavors != null && !flavors.isEmpty();
    }
}
