package com.restaurante.pedidos.service;

import com.restaurante.pedidos.config.PedidosProperties;
import com.restaurante.pedidos.domain.AdminEvent;
import com.restaurante.pedidos.domain.AdminEventType;
import com.restaurante.pedidos.domain.BackupRecord;
import com.restaurante.pedidos.dto.SystemDtos;
import com.restaurante.pedidos.exception.BusinessException;
import com.restaurante.pedidos.repository.AdminEventRepository;
import com.restaurante.pedidos.repository.BackupRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * Copias de seguridad de la base de datos H2.
 *
 * <p>Usa la sentencia {@code BACKUP TO} de H2, que genera una copia <b>consistente</b> sin
 * detener la aplicacion y sin copiar a mano un archivo de base de datos activo (lo que
 * produciria una copia inconsistente).</p>
 *
 * <p><b>Restauracion (documentada en el README):</b> se debe detener la aplicacion,
 * conservar el archivo {@code data/pedidos.mv.db} actual y restaurar desde la copia usando
 * {@code DROP ALL OBJECTS; RUNSCRIPT FROM '<copia.zip>'} o reemplazando el archivo. Nunca se
 * borra la base de datos automaticamente.</p>
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final JdbcTemplate jdbcTemplate;
    private final BackupRecordRepository backupRepository;
    private final AdminEventRepository adminEventRepository;
    private final PedidosProperties properties;

    public BackupService(JdbcTemplate jdbcTemplate,
                         BackupRecordRepository backupRepository,
                         AdminEventRepository adminEventRepository,
                         PedidosProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.backupRepository = backupRepository;
        this.adminEventRepository = adminEventRepository;
        this.properties = properties;
    }

    /**
     * Crea una copia de seguridad online y consistente. No requiere detener la aplicacion.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SystemDtos.BackupView createBackup(String actor) {
        String fileName = "pedidos-" + LocalDateTime.now().format(STAMP) + ".zip";
        Path dir = Path.of(properties.getBackup().getDir());
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName).toAbsolutePath();
            // BACKUP TO no admite parametros; el nombre lo generamos nosotros sin entrada del usuario.
            jdbcTemplate.execute("BACKUP TO '" + target.toString().replace("'", "''") + "'");
            long size = Files.exists(target) ? Files.size(target) : 0L;
            BackupRecord record = backupRepository.save(new BackupRecord(fileName, size, true,
                    "Copia creada en " + target));
            adminEventRepository.save(new AdminEvent(AdminEventType.BACKUP_CREADO,
                    "Copia de seguridad creada: " + fileName, actor));
            return toView(record);
        } catch (Exception e) {
            log.error("Error creando copia de seguridad: {}", e.getMessage());
            BackupRecord record = backupRepository.save(new BackupRecord(fileName, null, false, e.getMessage()));
            adminEventRepository.save(new AdminEvent(AdminEventType.BACKUP_ERROR,
                    "Error al crear copia: " + e.getMessage(), actor));
            return toView(record);
        }
    }

    @Transactional(readOnly = true)
    public List<SystemDtos.BackupView> list() {
        List<SystemDtos.BackupView> out = new ArrayList<>();
        for (BackupRecord r : backupRepository.findTop50ByOrderByCreatedAtDesc()) {
            out.add(toView(r));
        }
        return out;
    }

    /**
     * Verifica de forma basica que el archivo de copia existe y es un ZIP legible
     * (H2 genera las copias como archivo zip con el script de restauracion).
     */
    @Transactional(readOnly = true)
    public boolean verifyBackup(String fileName) {
        Path path = Path.of(properties.getBackup().getDir()).resolve(fileName);
        if (!Files.exists(path)) {
            return false;
        }
        try (ZipFile zip = new ZipFile(path.toFile())) {
            return zip.entries().hasMoreElements();
        } catch (Exception e) {
            return false;
        }
    }

    public Path backupDirectory() {
        return Path.of(properties.getBackup().getDir()).toAbsolutePath();
    }

    public SystemDtos.BackupView lastBackup() {
        return backupRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .findFirst()
                .map(this::toView)
                .orElseGet(() -> new SystemDtos.BackupView(null, null, null, false, "Nunca", null));
    }

    private SystemDtos.BackupView toView(BackupRecord r) {
        return new SystemDtos.BackupView(
                r.getId(), r.getFileName(), r.getSizeBytes(), r.isSuccess(), r.getMessage(),
                r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
    }

    /** Lanza si el nombre de archivo intenta salir del directorio de copias. */
    public void validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.contains("..")
                || fileName.contains("/") || fileName.contains("\\")) {
            throw new BusinessException("Nombre de copia no válido.");
        }
    }
}
