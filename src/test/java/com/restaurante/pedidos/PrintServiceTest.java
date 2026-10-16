package com.restaurante.pedidos;

import com.restaurante.pedidos.dto.OrderDtos;
import com.restaurante.pedidos.dto.SystemDtos;
import com.restaurante.pedidos.repository.ProductRepository;
import com.restaurante.pedidos.service.OrderService;
import com.restaurante.pedidos.service.PrintService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de impresion en modo SIMULACION (no hay impresora fisica disponible):
 * el trabajo se marca ACEPTADO por la cola simulada, pero NUNCA se afirma que salio en papel
 * ({@code physicalConfirmed=false}); el doble clic no duplica; y el reintento genera un nuevo
 * intento con su propio identificador.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PrintServiceTest {

    @Autowired PrintService printService;
    @Autowired OrderService orderService;
    @Autowired ProductRepository productRepository;

    private OrderDtos.OrderView nuevoPedido() {
        Long aguaId = productRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(p -> p.getName().equalsIgnoreCase("Agua")).findFirst().orElseThrow().getId();
        return orderService.createOrder(new OrderDtos.CreateOrderRequest(
                "LLEVAR", null, "Cliente", null, null, null, null,
                List.of(new OrderDtos.CreateItemRequest(aguaId, 1, null, null, null)), null), "prueba");
    }

    @Test
    void enSimulacionQuedaAceptadoPeroNoConfirmadoFisicamente() {
        OrderDtos.OrderView pedido = nuevoPedido();

        SystemDtos.PrintJobView job = printService.requestPrint(
                pedido.id(), new SystemDtos.PrintRequest("FULL", null, false, null), "prueba");

        assertEquals("ACEPTADO", job.status());
        assertTrue(job.simulated(), "En modo simulacion el trabajo debe indicar simulated=true.");
        assertFalse(job.physicalConfirmed(), "Nunca se debe afirmar impresion fisica en simulacion.");
        assertTrue(job.message() != null && job.message().contains("SIMULACIÓN"),
                "El mensaje debe indicar claramente que es una simulacion.");
        assertEquals(1, job.attempt());
        assertTrue(job.content() != null && job.content().contains(pedido.code()));
    }

    @Test
    void elDobleClicNoDuplicaElTrabajo() {
        OrderDtos.OrderView pedido = nuevoPedido();

        SystemDtos.PrintJobView primero = printService.requestPrint(
                pedido.id(), new SystemDtos.PrintRequest("FULL", null, false, null), "prueba");
        SystemDtos.PrintJobView segundo = printService.requestPrint(
                pedido.id(), new SystemDtos.PrintRequest("FULL", null, false, null), "prueba");

        assertEquals(primero.id(), segundo.id(),
                "Dos solicitudes identicas seguidas (doble clic) deben devolver el mismo trabajo.");
    }

    @Test
    void elReintentoGeneraUnNuevoIntento() {
        OrderDtos.OrderView pedido = nuevoPedido();
        SystemDtos.PrintJobView original = printService.requestPrint(
                pedido.id(), new SystemDtos.PrintRequest("FULL", null, false, null), "prueba");

        SystemDtos.PrintJobView reintento = printService.retryPrint(original.id(), "prueba");

        assertNotEquals(original.id(), reintento.id(), "El reintento debe ser un trabajo nuevo.");
        assertEquals(original.attempt() + 1, reintento.attempt());
        assertTrue(reintento.reprint());
    }

    @Test
    void laImpresionPorAreaEsPosible() {
        OrderDtos.OrderView pedido = nuevoPedido();
        Long areaId = orderService.getById(pedido.id()).items().get(0).tasks().get(0).areaId();

        SystemDtos.PrintJobView job = printService.requestPrint(
                pedido.id(), new SystemDtos.PrintRequest("AREA", areaId, false, null), "prueba");

        assertEquals("ACEPTADO", job.status());
        assertEquals(areaId, job.areaId());
    }
}
