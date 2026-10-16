package com.restaurante.pedidos;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Verifica que el contexto de Spring arranca correctamente con el perfil de pruebas. */
@SpringBootTest
@ActiveProfiles("test")
class PedidosApplicationTests {

    @Test
    void contextLoads() {
        // Si el contexto arranca, la prueba pasa.
    }
}
