package com.restaurante.pedidos;

import com.restaurante.pedidos.domain.LockConfig;
import com.restaurante.pedidos.repository.LockConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica a nivel HTTP que, con el sistema bloqueado, las API de negocio responden 423 (Locked)
 * con el mensaje exacto, mientras que las rutas de desbloqueo/estado siguen accesibles.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class LockEnforcementTest {

    @Autowired MockMvc mvc;
    @Autowired LockConfigRepository lockConfigRepository;

    private void forzarVencimiento() {
        LockConfig config = lockConfigRepository.findById(LockConfig.SINGLETON_ID).orElseThrow();
        config.setNextDueAt(LocalDateTime.now().minusDays(1));
        lockConfigRepository.save(config);
    }

    @Test
    void conElSistemaBloqueadoLasApisDeNegocioResponden423() throws Exception {
        forzarVencimiento();

        mvc.perform(get("/api/orders"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error").value("locked"))
                .andExpect(jsonPath("$.message").value("Página bloqueada, ingrese contraseña"));

        mvc.perform(get("/api/dashboard")).andExpect(status().isLocked());
        mvc.perform(get("/api/tables")).andExpect(status().isLocked());
    }

    @Test
    void conElSistemaBloqueadoLasRutasDeDesbloqueoSiguenAccesibles() throws Exception {
        forzarVencimiento();

        mvc.perform(get("/api/lock/status")).andExpect(status().isOk());
        mvc.perform(get("/api/session")).andExpect(status().isOk());
    }

    @Test
    void sinBloqueoLasApisDeNegocioFuncionan() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isOk());
    }
}
