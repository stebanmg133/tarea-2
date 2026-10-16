package com.restaurante.pedidos.service;

import com.restaurante.pedidos.domain.OrderStatus;
import com.restaurante.pedidos.domain.PrintJobStatus;
import com.restaurante.pedidos.dto.SystemDtos;
import com.restaurante.pedidos.repository.OrderRepository;
import com.restaurante.pedidos.repository.PrintJobRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Estado del sistema para la pantalla de diagnostico: base de datos, impresion, backups y
 * modulo de bloqueo. No expone secretos.
 */
@Service
public class SystemStatusService {

    private static final EnumSet<OrderStatus> ACTIVE =
            EnumSet.of(OrderStatus.PENDIENTE, OrderStatus.EN_PREPARACION, OrderStatus.LISTO);

    private final OrderRepository orderRepository;
    private final PrintJobRepository printJobRepository;
    private final BackupService backupService;
    private final LockService lockService;
    private final PrintService printService;
    private final JdbcTemplate jdbcTemplate;

    public SystemStatusService(OrderRepository orderRepository,
                               PrintJobRepository printJobRepository,
                               BackupService backupService,
                               LockService lockService,
                               PrintService printService,
                               JdbcTemplate jdbcTemplate) {
        this.orderRepository = orderRepository;
        this.printJobRepository = printJobRepository;
        this.backupService = backupService;
        this.lockService = lockService;
        this.printService = printService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public SystemDtos.SystemStatusView status() {
        boolean dbOk = true;
        String product = "H2";
        String version = "?";
        try {
            product = jdbcTemplate.queryForObject("select H2VERSION()", String.class);
            version = product;
        } catch (Exception e) {
            dbOk = false;
        }

        long totalOrders = orderRepository.count();
        long activeOrders = orderRepository.countByStatusIn(new ArrayList<>(ACTIVE));
        long printTotal = printJobRepository.count();
        long printErrors = printJobRepository.countByStatusIn(
                List.of(PrintJobStatus.ERROR, PrintJobStatus.DESCONOCIDO));
        SystemDtos.BackupView lastBackup = backupService.lastBackup();
        SystemDtos.LockStatusView lock = lockService.status();

        return new SystemDtos.SystemStatusView(
                dbOk,
                product,
                version,
                LocalDateTime.now().toString(),
                Path.of("data").toAbsolutePath().toString(),
                totalOrders,
                activeOrders,
                printService.isSimulationMode() ? "SIMULATION" : "REAL",
                printTotal,
                printErrors,
                lastBackup.createdAt(),
                lastBackup.fileName(),
                lock.moduleEnabled(),
                lock.locked(),
                lock.nextDueAt()
        );
    }
}
