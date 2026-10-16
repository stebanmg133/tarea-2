package com.restaurante.pedidos.service;

import com.restaurante.pedidos.domain.Category;
import com.restaurante.pedidos.domain.Flavor;
import com.restaurante.pedidos.domain.PreparationArea;
import com.restaurante.pedidos.domain.Product;
import com.restaurante.pedidos.domain.ProductVariant;
import com.restaurante.pedidos.dto.CatalogDtos;
import com.restaurante.pedidos.exception.BusinessException;
import com.restaurante.pedidos.exception.NotFoundException;
import com.restaurante.pedidos.repository.CategoryRepository;
import com.restaurante.pedidos.repository.PreparationAreaRepository;
import com.restaurante.pedidos.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Gestion del catalogo: productos, categorias, areas, sabores y variantes.
 * Los productos inactivos no se ofrecen al tomar pedidos nuevos pero permanecen en los
 * pedidos historicos (los articulos guardan su propio nombre/precio).
 */
@Service
public class CatalogService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final PreparationAreaRepository areaRepository;

    public CatalogService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          PreparationAreaRepository areaRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.areaRepository = areaRepository;
    }

    // --- Areas -------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CatalogDtos.AreaView> listAreas(boolean onlyActive) {
        List<PreparationArea> areas = onlyActive
                ? areaRepository.findByActiveTrueOrderBySortOrderAscIdAsc()
                : areaRepository.findAllByOrderBySortOrderAscIdAsc();
        List<CatalogDtos.AreaView> out = new ArrayList<>();
        for (PreparationArea a : areas) {
            out.add(new CatalogDtos.AreaView(a.getId(), a.getCode(), a.getName(), a.isActive(), a.getSortOrder()));
        }
        return out;
    }

    @Transactional
    public CatalogDtos.AreaView createArea(String code, String name, Integer sortOrder) {
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw new BusinessException("El área requiere código y nombre.");
        }
        String normalized = code.trim().toUpperCase(java.util.Locale.ROOT);
        if (areaRepository.findByCode(normalized).isPresent()) {
            throw new BusinessException("Ya existe un área con el código " + normalized);
        }
        PreparationArea area = new PreparationArea(normalized, name.trim(), sortOrder == null ? 0 : sortOrder);
        area = areaRepository.save(area);
        return new CatalogDtos.AreaView(area.getId(), area.getCode(), area.getName(), area.isActive(), area.getSortOrder());
    }

    @Transactional
    public CatalogDtos.AreaView updateArea(Long id, String name, Boolean active, Integer sortOrder) {
        PreparationArea area = areaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Area no encontrada"));
        if (name != null && !name.isBlank()) {
            area.setName(name.trim());
        }
        if (active != null) {
            area.setActive(active);
        }
        if (sortOrder != null) {
            area.setSortOrder(sortOrder);
        }
        area = areaRepository.save(area);
        return new CatalogDtos.AreaView(area.getId(), area.getCode(), area.getName(), area.isActive(), area.getSortOrder());
    }

    // --- Categorias --------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CatalogDtos.CategoryView> listCategories(boolean onlyActive, boolean onlyQuick) {
        List<Category> categories;
        if (onlyQuick) {
            categories = categoryRepository.findByQuickTrueAndActiveTrueOrderBySortOrderAscIdAsc();
        } else if (onlyActive) {
            categories = categoryRepository.findByActiveTrueOrderBySortOrderAscIdAsc();
        } else {
            categories = categoryRepository.findAllByOrderBySortOrderAscIdAsc();
        }
        List<CatalogDtos.CategoryView> out = new ArrayList<>();
        for (Category c : categories) {
            out.add(toCategoryView(c));
        }
        return out;
    }

    private CatalogDtos.CategoryView toCategoryView(Category c) {
        return new CatalogDtos.CategoryView(c.getId(), c.getCode(), c.getName(), c.isQuick(), c.isActive(), c.getSortOrder());
    }

    // --- Productos ---------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CatalogDtos.ProductView> listProducts(boolean onlyActive) {
        List<Product> products = onlyActive
                ? productRepository.findByActiveTrueOrderBySortOrderAscIdAsc()
                : productRepository.findAllByOrderBySortOrderAscIdAsc();
        List<CatalogDtos.ProductView> out = new ArrayList<>();
        for (Product p : products) {
            out.add(toProductView(p));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public CatalogDtos.ProductView getProduct(Long id) {
        return toProductView(productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado")));
    }

    @Transactional
    public CatalogDtos.ProductView createProduct(CatalogDtos.ProductRequest req) {
        Product product = new Product();
        applyProduct(product, req);
        product = productRepository.save(product);
        return toProductView(product);
    }

    @Transactional
    public CatalogDtos.ProductView updateProduct(Long id, CatalogDtos.ProductRequest req) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
        applyProduct(product, req);
        product = productRepository.save(product);
        return toProductView(product);
    }

    @Transactional
    public CatalogDtos.ProductView setProductActive(Long id, boolean active) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
        product.setActive(active);
        product = productRepository.save(product);
        return toProductView(product);
    }

    private void applyProduct(Product product, CatalogDtos.ProductRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new BusinessException("El producto requiere un nombre.");
        }
        product.setName(req.name().trim());
        if (req.basePrice() == null || req.basePrice().signum() < 0) {
            throw new BusinessException("El precio base debe ser mayor o igual a cero.");
        }
        product.setBasePrice(req.basePrice().setScale(2, java.math.RoundingMode.HALF_UP));
        product.setDescription(req.description());
        if (req.sortOrder() != null) {
            product.setSortOrder(req.sortOrder());
        }
        if (req.active() != null) {
            product.setActive(req.active());
        }
        if (req.categoryId() != null) {
            Category category = categoryRepository.findById(req.categoryId())
                    .orElseThrow(() -> new BusinessException("Categoria no encontrada"));
            product.setCategory(category);
        } else {
            product.setCategory(null);
        }

        // Sabores
        product.getFlavors().clear();
        boolean hasFlavors = req.flavors() != null && !req.flavors().isEmpty();
        product.setRequiresFlavor(hasFlavors && Boolean.TRUE.equals(req.requiresFlavor()));
        if (hasFlavors) {
            int i = 0;
            for (CatalogDtos.FlavorRequest fr : req.flavors()) {
                if (fr.name() == null || fr.name().isBlank()) {
                    continue;
                }
                BigDecimal delta = fr.priceDelta() == null ? BigDecimal.ZERO : fr.priceDelta();
                product.addFlavor(new Flavor(fr.name().trim(), delta.setScale(2, java.math.RoundingMode.HALF_UP), i++));
            }
        }

        // Variantes
        product.getVariants().clear();
        if (req.variants() != null) {
            int i = 0;
            for (CatalogDtos.VariantRequest vr : req.variants()) {
                if (vr.name() == null || vr.name().isBlank() || vr.price() == null) {
                    continue;
                }
                ProductVariant variant = new ProductVariant(vr.name().trim(),
                        vr.price().setScale(2, java.math.RoundingMode.HALF_UP), i++);
                if (vr.active() != null) {
                    variant.setActive(vr.active());
                }
                product.addVariant(variant);
            }
        }

        // Destinos (areas). Al menos una area es deseable; si no, se usara el area por defecto.
        Set<PreparationArea> areas = new LinkedHashSet<>();
        if (req.areaIds() != null) {
            for (Long areaId : req.areaIds()) {
                areaRepository.findById(areaId).ifPresent(areas::add);
            }
        }
        product.setAreas(areas);
    }

    private CatalogDtos.ProductView toProductView(Product p) {
        List<CatalogDtos.FlavorView> flavors = new ArrayList<>();
        for (Flavor f : p.getFlavors()) {
            flavors.add(new CatalogDtos.FlavorView(f.getId(), f.getName(), f.getPriceDelta()));
        }
        List<CatalogDtos.VariantView> variants = new ArrayList<>();
        for (ProductVariant v : p.getVariants()) {
            variants.add(new CatalogDtos.VariantView(v.getId(), v.getName(), v.getPrice(), v.isActive(), v.getSortOrder()));
        }
        List<Long> areaIds = new ArrayList<>();
        List<String> areaNames = new ArrayList<>();
        for (PreparationArea a : p.getAreas()) {
            areaIds.add(a.getId());
            areaNames.add(a.getName());
        }
        Category c = p.getCategory();
        return new CatalogDtos.ProductView(
                p.getId(),
                p.getName(),
                c != null ? c.getId() : null,
                c != null ? c.getName() : null,
                c != null ? c.getCode() : null,
                p.getBasePrice(),
                p.isActive(),
                p.isRequiresFlavor(),
                p.hasFlavors(),
                p.hasVariants(),
                p.getDescription(),
                p.getSortOrder(),
                flavors,
                variants,
                areaIds,
                areaNames
        );
    }
}
