package com.restaurante.pedidos;

import com.restaurante.pedidos.domain.LockConfig;
import com.restaurante.pedidos.dto.OrderDtos;
import com.restaurante.pedidos.dto.SystemDtos;
import com.restaurante.pedidos.exception.BusinessException;
import com.restaurante.pedidos.repository.LockConfigRepository;
import com.restaurante.pedidos.repository.OrderRepository;
import com.restaurante.pedidos.repository.ProductRepository;
import com.restaurante.pedidos.service.LockService;
import com.restaurante.pedidos.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del modulo de bloqueo temporal ("hombre muerto"). La credencial inicial de prueba
 * es {@code clavePrueba123} (ver {@code src/test/resources/test-security.properties}).
 *
 * <p>Se verifica, entre otras cosas, que el bloqueo NUNCA destruye pedidos.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LockModuleTest {

    private static final String CLAVE_INICIAL = "clavePrueba123";

    @Autowired LockService lockService;
    @Autowired LockConfigRepository lockConfigRepository;
    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;
    @Autowired ProductRepository productRepository;

    private void forzarVencimiento() {
        LockConfig config = lockConfigRepository.findById(LockConfig.SINGLETON_ID).orElseThrow();
        config.setNextDueAt(LocalDateTime.now().minusDays(1));
        lockConfigRepository.save(config);
    }

    @Test
    void estadoInicialEsActivoSinBloqueo() {
        assertFalse(lockService.isLocked(), "El sistema no debe estar bloqueado al inicio.");
        assertEquals("Sistema activo", lockService.status().message());
        assertTrue(lockService.status().nextDueAt() != null);
    }

    @Test
    void validaClaveCorrectaYRenuevaElPeriodo() {
        assertTrue(lockService.verifyPassword(CLAVE_INICIAL));

        SystemDtos.LockStatusView status = lockService.validateAndRenew(CLAVE_INICIAL, "prueba");

        assertFalse(status.locked());
        assertTrue(LocalDateTime.parse(status.nextDueAt()).isAfter(LocalDateTime.now()),
                "Tras renovar, la proxima validacion debe estar en el futuro.");
    }

    @Test
    void rechazaClaveIncorrecta() {
        assertThrows(BusinessException.class, () -> lockService.validateAndRenew("clave-equivocada", "prueba"));
    }

    @Test
    void bloqueoMuestraElMensajeExactoYNoDestruyePedidos() {
        Long aguaId = productRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(p -> p.getName().equalsIgnoreCase("Agua")).findFirst().orElseThrow().getId();
        OrderDtos.OrderView pedido = orderService.createOrder(new OrderDtos.CreateOrderRequest(
                "LLEVAR", null, "Cliente", null, null, null, null,
                List.of(new OrderDtos.CreateItemRequest(aguaId, 1, null, null, null)), null), "prueba");

        forzarVencimiento();

        assertTrue(lockService.isLocked(), "Con la fecha vencida el sistema debe bloquearse.");
        assertEquals(LockService.LOCKED_MESSAGE, lockService.status().message());
        assertEquals("Página bloqueada, ingrese contraseña", lockService.status().message(),
                "El mensaje de bloqueo debe ser exactamente el requerido.");
        assertTrue(orderRepository.findById(pedido.id()).isPresent(),
                "El bloqueo jamas debe borrar pedidos existentes.");
    }

    @Test
    void desbloquearConClaveCorrectaRestableceElFuncionamiento() {
        forzarVencimiento();
        assertTrue(lockService.isLocked());

        lockService.validateAndRenew(CLAVE_INICIAL, "prueba");

        assertFalse(lockService.isLocked(), "Tras validar la clave, el sistema debe quedar desbloqueado.");
    }

    @Test
    void cambiaLaContrasenaPrincipalYUsaLaNueva() {
        lockService.changeMainPassword(CLAVE_INICIAL, "nuevaClave456", "nuevaClave456", "prueba");

        assertTrue(lockService.verifyPassword("nuevaClave456"));
        assertFalse(lockService.verifyPassword(CLAVE_INICIAL));
        assertFalse(lockService.status().mustChangePassword(), "Tras cambiarla, ya no debe exigir cambio.");
    }

    @Test
    void rechazaCambioDeContrasenaDemasiadoCorta() {
        assertThrows(BusinessException.class,
                () -> lockService.changeMainPassword(CLAVE_INICIAL, "123", "123", "prueba"));
    }

    @Test
    void elIntervaloEsConfigurableYRecalculaLaProximaFecha() {
        SystemDtos.LockStatusView status = lockService.updateSettings(
                new SystemDtos.LockSettingsRequest(true, 3, "admin2@restaurante.local"), "prueba");

        assertEquals(3, status.intervalMonths());
        assertEquals("admin2@restaurante.local", status.adminEmail());
        assertTrue(LocalDateTime.parse(status.nextDueAt()).isAfter(LocalDateTime.now()));
    }

    @Test
    void desactivarElModuloImpideElBloqueo() {
        lockService.updateSettings(new SystemDtos.LockSettingsRequest(false, null, null), "prueba");
        forzarVencimiento();

        assertFalse(lockService.isLocked(),
                "Si el modulo esta desactivado, la fecha vencida no debe bloquear.");
    }
}
