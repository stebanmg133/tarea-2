package com.restaurante.pedidos;

import com.restaurante.pedidos.dto.CatalogDtos;
import com.restaurante.pedidos.dto.OrderDtos;
import com.restaurante.pedidos.service.CatalogService;
import com.restaurante.pedidos.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del catalogo y de la distribucion por areas:
 * <ul>
 *   <li>un producto puede tener sabores, variantes y varias areas de destino;</li>
 *   <li>un producto inactivo no se ofrece para pedidos nuevos pero sigue en el catalogo completo;</li>
 *   <li>un producto con dos areas genera dos tareas de trabajo pero UN solo cobro.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CatalogAndAreasTest {

    @Autowired CatalogService catalogService;
    @Autowired OrderService orderService;

    private Long categoryId() {
        return catalogService.listCategories(false, false).get(0).id();
    }

    private Long areaId(String code) {
        return catalogService.listAreas(false).stream()
                .filter(a -> a.code().equals(code))
                .findFirst()
                .orElseThrow()
                .id();
    }

    @Test
    void creaProductoConSaboresVariantesYMultiplesAreas() {
        CatalogDtos.ProductRequest req = new CatalogDtos.ProductRequest(
                "Producto Prueba",
                categoryId(),
                new BigDecimal("10000"),
                true,
                true,
                "Producto de prueba",
                900,
                List.of(new CatalogDtos.FlavorRequest("Clasico", BigDecimal.ZERO),
                        new CatalogDtos.FlavorRequest("Especial", new BigDecimal("1500"))),
                List.of(new CatalogDtos.VariantRequest("Pequeno", new BigDecimal("8000"), true),
                        new CatalogDtos.VariantRequest("Grande", new BigDecimal("12000"), true)),
                List.of(areaId("PIZZAS"), areaId("COCINA")));

        CatalogDtos.ProductView view = catalogService.createProduct(req);

        assertTrue(view.hasFlavors(), "Debe registrar sabores.");
        assertTrue(view.hasVariants(), "Debe registrar variantes.");
        assertEquals(2, view.flavors().size());
        assertEquals(2, view.variants().size());
        assertEquals(2, view.areaIds().size(), "Debe quedar asociado a dos areas.");
        assertEquals(2, view.areaNames().size());
    }

    @Test
    void productoInactivoNoSeOfrecePeroPermaneceEnElCatalogo() {
        CatalogDtos.ProductView creado = catalogService.createProduct(new CatalogDtos.ProductRequest(
                "Producto Temporal", categoryId(), new BigDecimal("1000"), true, false,
                null, 950, List.of(), List.of(), List.of(areaId("BEBIDAS"))));

        catalogService.setProductActive(creado.id(), false);

        assertFalse(catalogService.listProducts(true).stream().anyMatch(p -> p.id().equals(creado.id())),
                "Un producto inactivo no debe ofrecerse para pedidos nuevos.");
        assertTrue(catalogService.listProducts(false).stream().anyMatch(p -> p.id().equals(creado.id())),
                "Un producto inactivo debe seguir visible en el catalogo completo/historico.");
    }

    @Test
    void productoConDosAreasGeneraDosTareasPeroUnSoloCobro() {
        CatalogDtos.ProductView prod = catalogService.createProduct(new CatalogDtos.ProductRequest(
                "Multi Area", categoryId(), new BigDecimal("5000"), true, false,
                null, 960, List.of(), List.of(), List.of(areaId("PIZZAS"), areaId("GASEOSAS"))));

        OrderDtos.OrderView view = orderService.createOrder(new OrderDtos.CreateOrderRequest(
                "LLEVAR", null, "Cliente", null, null, null, null,
                List.of(new OrderDtos.CreateItemRequest(prod.id(), 1, null, null, null)), null), "prueba");

        OrderDtos.ItemView item = view.items().get(0);
        assertEquals(2, item.tasks().size(), "Debe haber una tarea por area de destino.");
        assertEquals(2, item.areaNames().size());
        assertEquals(new BigDecimal("5000.00"), view.total(),
                "El articulo se cuenta una sola vez aunque tenga dos areas.");
    }
}
