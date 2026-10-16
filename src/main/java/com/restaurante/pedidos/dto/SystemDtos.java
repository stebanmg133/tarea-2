package com.restaurante.pedidos.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTOs de sistema: panel, mesas, impresion, bloqueo, administracion y backups.
 */
public final class SystemDtos {

    private SystemDtos() {
    }

    public record TableView(
            Long id,
            String code,
            boolean occupied,
            String currentOrderCode
    ) {
    }

    public record AreaWorkItem(
            Long taskId,
            Long orderId,
            String orderCode,
            String type,
            String typeLabel,
            String tableCode,
            String customerName,
            String productName,
            int quantity,
            String flavorName,
            String variantName,
            String itemNotes,
            String generalNotes,
            String itemStatus,
            String taskStatus,
            String createdAt
    ) {
    }

    public record DashboardView(
            long activeOrders,
            long pendingOrders,
            long inPreparationOrders,
            long readyOrders,
            long deliveredToday,
            long cancelledToday,
            long occupiedTables,
            long freeTables,
            long activeDeliveries,
            long printPending,
            long printErrors,
            long needsReprint,
            BigDecimal salesToday,
            boolean dbOk,
            String lastBackupAt,
            String lastBackupStatus,
            String serverTime
    ) {
    }

    public record PrintJobView(
            Long id,
            Long orderId,
            String orderCode,
            Long areaId,
            String areaName,
            String status,
            String statusLabel,
            String statusColor,
            int attempt,
            int sourceVersion,
            boolean reprint,
            boolean simulated,
            boolean physicalConfirmed,
            String printerName,
            String message,
            String createdAt,
            String completedAt,
            String content
    ) {
    }

    public record PrintRequest(
            /** "FULL" o "AREA". */
            String target,
            Long areaId,
            Boolean reprint,
            String clientToken
    ) {
    }

    public record PrintConfigView(
            String mode,
            String printerName,
            int paperWidth,
            boolean realModeAvailable,
            List<String> availablePrinters
    ) {
    }

    public record PrintConfigRequest(
            String printerName,
            Integer paperWidth
    ) {
    }

    public record LockStatusView(
            boolean moduleEnabled,
            boolean locked,
            String nextDueAt,
            String lastValidationAt,
            int intervalMonths,
            String adminEmail,
            boolean mustChangePassword,
            boolean tempPasswordActive,
            String tempPasswordExpiresAt,
            String message
    ) {
    }

    public record UnlockRequest(
            String password
    ) {
    }

    public record RenewRequest(
            String password
    ) {
    }

    public record ChangePasswordRequest(
            String currentPassword,
            String newPassword,
            String confirmPassword
    ) {
    }

    public record TempPasswordRequest(
            String password,
            Integer validHours
    ) {
    }

    public record AdminLoginRequest(
            String password
    ) {
    }

    public record LockSettingsRequest(
            Boolean moduleEnabled,
            Integer intervalMonths,
            String adminEmail
    ) {
    }

    public record SettingView(
            String businessName,
            String currencySymbol,
            BigDecimal defaultDeliveryFee
    ) {
    }

    public record SettingRequest(
            String businessName,
            String currencySymbol,
            BigDecimal defaultDeliveryFee
    ) {
    }

    public record BackupView(
            Long id,
            String fileName,
            Long sizeBytes,
            boolean success,
            String message,
            String createdAt
    ) {
    }

    public record AdminSessionView(
            boolean authenticated,
            String csrfToken,
            boolean mustChangePassword,
            boolean locked
    ) {
    }

    public record AdminEventView(
            Long id,
            String type,
            String typeLabel,
            String description,
            String actor,
            String createdAt
    ) {
    }

    public record SystemStatusView(
            boolean dbOk,
            String dbProduct,
            String dbVersion,
            String serverTime,
            String dataPath,
            long totalOrders,
            long activeOrders,
            String printMode,
            long printJobsTotal,
            long printJobsError,
            String lastBackupAt,
            String lastBackupName,
            boolean lockModuleEnabled,
            boolean locked,
            String nextDueAt
    ) {
    }
}
