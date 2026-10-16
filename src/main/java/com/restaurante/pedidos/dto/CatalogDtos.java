package com.restaurante.pedidos.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTOs del catalogo (productos, categorias, areas, sabores y variantes).
 */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record AreaView(
            Long id,
            String code,
            String name,
            boolean active,
            int sortOrder
    ) {
    }

    public record CategoryView(
            Long id,
            String code,
            String name,
            boolean quick,
            boolean active,
            int sortOrder
    ) {
    }

    public record FlavorView(
            Long id,
            String name,
            BigDecimal priceDelta
    ) {
    }

    public record VariantView(
            Long id,
            String name,
            BigDecimal price,
            boolean active,
            int sortOrder
    ) {
    }

    public record ProductView(
            Long id,
            String name,
            Long categoryId,
            String categoryName,
            String categoryCode,
            BigDecimal basePrice,
            boolean active,
            boolean requiresFlavor,
            boolean hasFlavors,
            boolean hasVariants,
            String description,
            int sortOrder,
            List<FlavorView> flavors,
            List<VariantView> variants,
            List<Long> areaIds,
            List<String> areaNames
    ) {
    }

    public record FlavorRequest(
            String name,
            BigDecimal priceDelta
    ) {
    }

    public record VariantRequest(
            String name,
            BigDecimal price,
            Boolean active
    ) {
    }

    public record ProductRequest(
            String name,
            Long categoryId,
            BigDecimal basePrice,
            Boolean active,
            Boolean requiresFlavor,
            String description,
            Integer sortOrder,
            List<FlavorRequest> flavors,
            List<VariantRequest> variants,
            List<Long> areaIds
    ) {
    }
}
