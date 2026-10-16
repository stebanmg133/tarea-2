package com.restaurante.pedidos;

import com.restaurante.pedidos.domain.Product;
import com.restaurante.pedidos.dto.OrderDtos;
import com.restaurante.pedidos.exception.BusinessException;
import com.restaurante.pedidos.repository.OrderRepository;
import com.restaurante.pedidos.repository.ProductRepository;
import com.restaurante.pedidos.repository.RestaurantTableRepository;
import com.restaurante.pedidos.service.OrderService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del flujo de pedidos: creacion por tipo, calculo de totales en el backend,
 * ocupacion/liberacion de mesas, estados derivados, idempotencia y edicion de articulos.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderFlowTest {

    @Autowired OrderService orderService;
    @Autowired ProductRepository productRepository;
    @Autowired RestaurantTableRepository tableRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired EntityManager em;

    // ---------------------------------------------------------------- helpers

    private Product product(String name) {
        return productRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(p -> p.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Producto de prueba no encontrado: " + name));
    }

    private OrderDtos.CreateItemRequest item(Long productId, int qty, String flavor, String variant) {
        return new OrderDtos.CreateItemRequest(productId, qty, flavor, variant, null);
    }

    private OrderDtos.CreateOrderRequest llevar(OrderDtos.CreateItemRequest item, String clientToken) {
        return new OrderDtos.CreateOrderRequest("LLEVAR", null, "Cliente", "3001234567",
                null, null, null, List.of(item), clientToken);
    }

    private OrderDtos.OrderView reload(Long id) {
        em.flush();
        return orderService.getById(id);
    }

    // ---------------------------------------------------------------- pruebas

    @Test
    void creaPedidoParaLlevarConCodigoSecuencial() {
        OrderDtos.OrderView view = orderService.createOrder(llevar(item(product("Agua").getId(), 1, null, null), null), "test");
        assertNotNull(view.id());
        assertTrue(view.code().matches("PED-\\d{3,}"), "El codigo debe tener formato PED-###: " + view.code());
        assertEquals("LLEVAR", view.type());
    }

    @Test
    void pedidoMesaOcupaMesaYCalculaTotalEnServidor() {
        Product pizza = product("Pizza");
        // Precio Mediana 32000 + recargo Pepperoni 2000 = 34000
        OrderDtos.CreateOrderRequest req = new OrderDtos.CreateOrderRequest(
                "MESA", "M01", "Ana", "3001112233", null, null, null,
                List.of(item(pizza.getId(), 1, "Pepperoni", "Mediana")), null);

        OrderDtos.OrderView view = orderService.createOrder(req, "test");

        assertEquals(new BigDecimal("34000.00"), view.total());
        assertEquals("M01", view.tableCode());
        assertTrue(tableRepository.findByCode("M01").orElseThrow().isOccupied(),
                "La mesa M01 debe quedar ocupada.");
    }

    @Test
    void pedidoDomicilioSumaElDomicilioAlTotal() {
        Long aguaId = product("Agua").getId();
        OrderDtos.CreateOrderRequest req = new OrderDtos.CreateOrderRequest(
                "DOMICILIO", null, "Luis", "3009998888", "Calle 10 # 5-20", new BigDecimal("5000"),
                null, List.of(item(aguaId, 2, null, null)), null);

        OrderDtos.OrderView view = orderService.createOrder(req, "test");

        assertEquals(new BigDecimal("6000.00"), view.subtotal());
        assertEquals(new BigDecimal("5000.00"), view.deliveryFee());
        assertEquals(new BigDecimal("11000.00"), view.total());
    }

    @Test
    void pedidoParaLlevarNoCobraDomicilio() {
        OrderDtos.OrderView view = orderService.createOrder(
                llevar(item(product("Agua").getId(), 1, null, null), null), "test");
        assertEquals(new BigDecimal("0.00"), view.deliveryFee());
        assertEquals(new BigDecimal("3000.00"), view.total());
    }

    @Test
    void rechazaCantidadInvalida() {
        Long aguaId = product("Agua").getId();
        OrderDtos.CreateOrderRequest req = llevar(item(aguaId, 0, null, null), null);
        assertThrows(BusinessException.class, () -> orderService.createOrder(req, "test"));
    }

    @Test
    void noPermiteAgregarProductoInactivo() {
        Product agua = product("Agua");
        agua.setActive(false);
        productRepository.save(agua);

        OrderDtos.CreateOrderRequest req = llevar(item(agua.getId(), 1, null, null), null);
        assertThrows(BusinessException.class, () -> orderService.createOrder(req, "test"));
    }

    @Test
    void exigeSaborCuandoEsObligatorio() {
        Product pizza = product("Pizza"); // requiresFlavor = true
        OrderDtos.CreateOrderRequest req = new OrderDtos.CreateOrderRequest(
                "MESA", "M03", "Ana", null, null, null, null,
                List.of(item(pizza.getId(), 1, null, "Personal")), null);
        assertThrows(BusinessException.class, () -> orderService.createOrder(req, "test"));
    }

    @Test
    void cancelarConservaElPedidoYNoLoMarcaEntregado() {
        OrderDtos.OrderView creado = orderService.createOrder(
                llevar(item(product("Agua").getId(), 1, null, null), null), "test");

        OrderDtos.OrderView cancelado = orderService.cancelOrder(creado.id(), "Prueba", "test");

        assertEquals("CANCELADO", cancelado.status());
        assertNotNull(orderRepository.findById(creado.id()).orElse(null));
        assertEquals(null, cancelado.deliveredAt());
    }

    @Test
    void estadosSeDerivanYLiberanLaMesaAlEntregarTodo() {
        Product pizza = product("Pizza");
        OrderDtos.OrderView creado = orderService.createOrder(new OrderDtos.CreateOrderRequest(
                "MESA", "M02", "Ana", null, null, null, null,
                List.of(item(pizza.getId(), 1, "Hawaiana", "Personal")), null), "test");

        OrderDtos.OrderView conItems = reload(creado.id());
        Long itemId = conItems.items().get(0).id();
        assertNotNull(itemId, "El articulo debe tener id tras el flush");

        OrderDtos.OrderView entregado = orderService.updateItemStatus(creado.id(), itemId, null, "ENTREGADO", "test");

        assertEquals("ENTREGADO", entregado.status());
        assertFalse(tableRepository.findByCode("M02").orElseThrow().isOccupied(),
                "La mesa M02 debe liberarse al entregar todo.");
        assertNotNull(entregado.deliveredAt());
    }

    @Test
    void idempotenciaPorTokenEvitaPedidosDuplicados() {
        OrderDtos.CreateItemRequest it = item(product("Agua").getId(), 1, null, null);
        OrderDtos.OrderView primero = orderService.createOrder(llevar(it, "token-unico-123"), "test");
        OrderDtos.OrderView segundo = orderService.createOrder(llevar(it, "token-unico-123"), "test");

        assertEquals(primero.id(), segundo.id(), "El mismo token no debe crear un segundo pedido.");
    }

    @Test
    void cambiarCantidadNoBorraLasObservaciones() {
        Long aguaId = product("Agua").getId();
        OrderDtos.CreateItemRequest conNota = new OrderDtos.CreateItemRequest(aguaId, 1, null, null, "sin hielo");
        OrderDtos.OrderView creado = orderService.createOrder(
                new OrderDtos.CreateOrderRequest("LLEVAR", null, "Cliente", null, null, null, null,
                        List.of(conNota), null), "test");

        OrderDtos.OrderView conItems = reload(creado.id());
        Long itemId = conItems.items().get(0).id();

        // Actualizacion enviando solo la cantidad (sin observaciones).
        OrderDtos.OrderView actualizado = orderService.updateItem(creado.id(), itemId,
                new OrderDtos.CreateItemRequest(null, 3, null, null, null), "test");

        OrderDtos.ItemView itemView = actualizado.items().get(0);
        assertEquals(3, itemView.quantity());
        assertEquals("sin hielo", itemView.notes(), "Las observaciones no deben borrarse al cambiar la cantidad.");
        assertEquals(new BigDecimal("9000.00"), actualizado.total());
    }

    @Test
    void retirarArticuloRecalculaElTotalSinBorrarloDelPedido() {
        Long aguaId = product("Agua").getId();
        OrderDtos.CreateOrderRequest req = new OrderDtos.CreateOrderRequest(
                "LLEVAR", null, "Cliente", null, null, null, null,
                List.of(item(aguaId, 1, null, null), item(aguaId, 2, null, null)), null);
        OrderDtos.OrderView creado = orderService.createOrder(req, "test");
        assertEquals(new BigDecimal("9000.00"), creado.total());

        OrderDtos.OrderView conItems = reload(creado.id());
        Long itemId = conItems.items().get(0).id();

        OrderDtos.OrderView tras = orderService.removeItem(creado.id(), itemId, "test");

        assertEquals(new BigDecimal("6000.00"), tras.total());
        assertEquals(2, tras.items().size(), "El articulo retirado se conserva marcado como cancelado.");
        assertTrue(tras.items().stream().anyMatch(OrderDtos.ItemView::cancelled));
    }
}
