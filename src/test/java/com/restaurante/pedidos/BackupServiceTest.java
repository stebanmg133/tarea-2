package com.restaurante.pedidos;

import com.restaurante.pedidos.dto.SystemDtos;
import com.restaurante.pedidos.service.BackupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de copia de seguridad. Usa la base H2 de archivo de pruebas y la carpeta aislada
 * {@code target/test-backups}. No es transaccional: la copia debe persistir para verificarse.
 */
@SpringBootTest
@ActiveProfiles("test")
class BackupServiceTest {

    @Autowired BackupService backupService;

    @Test
    void creaUnaCopiaConsistenteYEsVerificable() {
        SystemDtos.BackupView view = backupService.createBackup("prueba");

        assertTrue(view.success(), "La copia debe completarse. Detalle: " + view.message());
        assertNotNull(view.fileName());
        assertTrue(view.sizeBytes() != null && view.sizeBytes() > 0, "La copia no debe estar vacia.");

        Path file = backupService.backupDirectory().resolve(view.fileName());
        assertTrue(Files.exists(file), "El archivo de copia debe existir en disco: " + file);
        assertTrue(backupService.verifyBackup(view.fileName()), "La copia debe superar la verificacion.");
    }
}
