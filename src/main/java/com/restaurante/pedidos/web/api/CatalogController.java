package com.restaurante.pedidos.web.api;

import com.restaurante.pedidos.dto.CatalogDtos;
import com.restaurante.pedidos.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Consulta del catalogo (lectura). Las modificaciones viven en {@code /api/admin/catalog}. */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/products")
    public List<CatalogDtos.ProductView> products(
            @RequestParam(value = "onlyActive", defaultValue = "true") boolean onlyActive) {
        return catalogService.listProducts(onlyActive);
    }

    @GetMapping("/products/{id}")
    public CatalogDtos.ProductView product(@PathVariable Long id) {
        return catalogService.getProduct(id);
    }

    @GetMapping("/categories")
    public List<CatalogDtos.CategoryView> categories(
            @RequestParam(value = "onlyActive", defaultValue = "true") boolean onlyActive,
            @RequestParam(value = "quick", defaultValue = "false") boolean quick) {
        return catalogService.listCategories(onlyActive, quick);
    }

    @GetMapping("/areas")
    public List<CatalogDtos.AreaView> areas(
            @RequestParam(value = "onlyActive", defaultValue = "true") boolean onlyActive) {
        return catalogService.listAreas(onlyActive);
    }
}
