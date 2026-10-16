package com.restaurante.pedidos;

import com.restaurante.pedidos.dto.OrderDtos;
import com.restaurante.pedidos.repository.ProductRepository;
import com.restaurante.pedidos.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica que la numeracion de pedidos es segura bajo concurrencia: varios "dispositivos"
 * creando pedidos a la vez no obtienen el mismo codigo ni pierden numeros.
 *
 * <p>No es transaccional a proposito: cada pedido debe confirmarse para forzar la contienda
 * real sobre la secuencia persistente.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderNumberingConcurrencyTest {

    @Autowired OrderService orderService;
    @Autowired ProductRepository productRepository;

    @Test
    void generaCodigosUnicosCuandoSeCreanPedidosEnParalelo() throws Exception {
        Long aguaId = productRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(p -> p.getName().equalsIgnoreCase("Agua"))
                .findFirst()
                .orElseThrow()
                .getId();

        int total = 12;
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < total; i++) {
                futures.add(pool.submit(() -> orderService.createOrder(
                        new OrderDtos.CreateOrderRequest("LLEVAR", null, "Concurrente", null,
                                null, null, null,
                                List.of(new OrderDtos.CreateItemRequest(aguaId, 1, null, null, null)),
                                null),
                        "prueba").code()));
            }

            Set<String> codigos = new HashSet<>();
            for (Future<String> f : futures) {
                codigos.add(f.get(60, TimeUnit.SECONDS));
            }

            assertEquals(total, codigos.size(), "Todos los codigos generados deben ser unicos.");
            assertTrue(codigos.stream().allMatch(c -> c.matches("PED-\\d{3,}")),
                    "Todos los codigos deben tener el formato PED-###.");
        } finally {
            pool.shutdownNow();
        }
    }
}
