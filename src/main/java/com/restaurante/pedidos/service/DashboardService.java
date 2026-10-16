package com.restaurante.pedidos.service;

import com.restaurante.pedidos.domain.BackupRecord;
import com.restaurante.pedidos.domain.Order;
import com.restaurante.pedidos.domain.OrderStatus;
import com.restaurante.pedidos.domain.OrderType;
import com.restaurante.pedidos.domain.PrintJobStatus;
import com.restaurante.pedidos.dto.SystemDtos;
import com.restaurante.pedidos.repository.BackupRecordRepository;
import com.restaurante.pedidos.repository.OrderRepository;
import com.restaurante.pedidos.repository.PrintJobRepository;
import com.restaurante.pedidos.repository.RestaurantTableRepository;
import com.restaurante.pedidos.repository.PreparationAreaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Panel operativo con informacion real. Las ventas del dia corresponden unicamente a
 * pedidos ENTREGADOS (nunca a cancelados) y se calculan con reglas explicitas.
 */
@Service
public class DashboardService {

    private static final EnumSet<OrderStatus> ACTIVE =
            EnumSet.of(OrderStatus.PENDIENTE, OrderStatus.EN_PREPARACION, OrderStatus.LISTO);

    private final OrderRepository orderRepository;
    private final RestaurantTableRepository tableRepository;
    private final PrintJobRepository printJobRepository;
    private final BackupRecordRepository backupRepository;
    private final PreparationAreaRepository areaRepository;
    private final DataSource dataSource;

    public DashboardService(OrderRepository orderRepository,
                            RestaurantTableRepository tableRepository,
                            PrintJobRepository printJobRepository,
                            BackupRecordRepository backupRepository,
                            PreparationAreaRepository areaRepository,
                            DataSource dataSource) {
        this.orderRepository = orderRepository;
        this.tableRepository = tableRepository;
        this.printJobRepository = printJobRepository;
        this.backupRepository = backupRepository;
        this.areaRepository = areaRepository;
        this.dataSource = dataSource;
    }

    @Transactional(readOnly = true)
    public SystemDtos.DashboardView dashboard() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        long activeOrders = orderRepository.countByStatusIn(new ArrayList<>(ACTIVE));
        long pending = orderRepository.countByStatus(OrderStatus.PENDIENTE);
        long inPrep = orderRepository.countByStatus(OrderStatus.EN_PREPARACION);
        long ready = orderRepository.countByStatus(OrderStatus.LISTO);
        long deliveredToday = orderRepository.countByStatusAndDeliveredAtBetween(
                OrderStatus.ENTREGADO, startOfDay, endOfDay);
        long cancelledToday = orderRepository.countByStatusAndCancelledAtBetween(
                OrderStatus.CANCELADO, startOfDay, endOfDay);

        long occupied = tableRepository.countByOccupiedTrue();
        long totalTables = tableRepository.count();
        long activeDeliveries = orderRepository.countByTypeAndStatusIn(OrderType.DOMICILIO, new ArrayList<>(ACTIVE));

        long printPending = printJobRepository.countByStatusIn(
                List.of(PrintJobStatus.PENDIENTE, PrintJobStatus.ENVIANDO, PrintJobStatus.REINTENTO_SOLICITADO));
        long printErrors = printJobRepository.countByStatusIn(
                List.of(PrintJobStatus.ERROR, PrintJobStatus.DESCONOCIDO));
        long needsReprint = orderRepository.findByNeedsReprintTrueAndStatusIn(new ArrayList<>(ACTIVE)).size();

        BigDecimal salesToday = orderRepository.sumDeliveredTotalBetween(startOfDay, endOfDay);
        if (salesToday == null) {
            salesToday = BigDecimal.ZERO;
        }

        BackupRecord lastBackup = backupRepository.findTop50ByOrderByCreatedAtDesc().stream().findFirst().orElse(null);

        return new SystemDtos.DashboardView(
                activeOrders,
                pending,
                inPrep,
                ready,
                deliveredToday,
                cancelledToday,
                occupied,
                Math.max(0, totalTables - occupied),
                activeDeliveries,
                printPending,
                printErrors,
                needsReprint,
                salesToday,
                checkDatabase(),
                lastBackup != null ? lastBackup.getCreatedAt().toString() : null,
                lastBackup != null ? (lastBackup.isSuccess() ? "OK" : "ERROR") : "Nunca",
                LocalDateTime.now().toString()
        );
    }

    /** Verifica que la base de datos responde. */
    public boolean checkDatabase() {
        try (Connection c = dataSource.getConnection()) {
            return c.isValid(3);
        } catch (Exception e) {
            return false;
        }
    }
}
