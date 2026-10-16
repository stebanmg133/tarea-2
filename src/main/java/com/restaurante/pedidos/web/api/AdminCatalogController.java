package com.restaurante.pedidos.web.api;

import com.restaurante.pedidos.dto.CatalogDtos;
import com.restaurante.pedidos.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Configuracion del catalogo (solo administracion): crear, editar, activar/desactivar
 * productos y areas. Un producto inactivo no se ofrece en pedidos nuevos pero permanece
 * visible en los pedidos historicos.
 */
@RestController
@RequestMapping("/api/admin/catalog")
public class AdminCatalogController {

    private final CatalogService catalogService;

    public AdminCatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/products")
    public List<CatalogDtos.ProductView> products(
            @RequestParam(value = "onlyActive", defaultValue = "false") boolean onlyActive) {
        return catalogService.listProducts(onlyActive);
    }

    @PostMapping("/products")
    public CatalogDtos.ProductView create(@RequestBody CatalogDtos.ProductRequest request) {
        return catalogService.createProduct(request);
    }

    @PutMapping("/products/{id}")
    public CatalogDtos.ProductView update(@PathVariable Long id,
                                          @RequestBody CatalogDtos.ProductRequest request) {
        return catalogService.updateProduct(id, request);
    }

    @PostMapping("/products/{id}/active")
    public CatalogDtos.ProductView setActive(@PathVariable Long id,
                                             @RequestParam boolean value) {
        return catalogService.setProductActive(id, value);
    }

    @PostMapping("/areas")
    public CatalogDtos.AreaView createArea(@RequestParam String code,
                                           @RequestParam String name,
                                           @RequestParam(required = false) Integer sortOrder) {
        return catalogService.createArea(code, name, sortOrder);
    }

    @PutMapping("/areas/{id}")
    public CatalogDtos.AreaView updateArea(@PathVariable Long id,
                                           @RequestParam(required = false) String name,
                                           @RequestParam(required = false) Boolean active,
                                           @RequestParam(required = false) Integer sortOrder) {
        return catalogService.updateArea(id, name, active, sortOrder);
    }
}
