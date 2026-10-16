package com.restaurante.pedidos.service;

import com.restaurante.pedidos.config.PedidosProperties;
import com.restaurante.pedidos.domain.AdminEvent;
import com.restaurante.pedidos.domain.AdminEventType;
import com.restaurante.pedidos.domain.LockConfig;
import com.restaurante.pedidos.dto.SystemDtos;
import com.restaurante.pedidos.exception.BusinessException;
import com.restaurante.pedidos.repository.AdminEventRepository;
import com.restaurante.pedidos.repository.LockConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Modulo de bloqueo temporal ("hombre muerto").
 *
 * <p>Bloquea el funcionamiento operativo cuando vence el periodo sin la validacion
 * correspondiente. El estado y la fecha se calculan y se guardan en el servidor
 * (tabla {@code lock_config}); no dependen de JavaScript ni de la sesion del navegador, y
 * persisten tras reiniciar el servidor.</p>
 *
 * <p>Nunca borra, cifra ni inutiliza datos: los pedidos permanecen intactos.</p>
 *
 * <p><b>Regla de vencimiento:</b> al validar correctamente, {@code nextDueAt = momento de
 * validacion + intervalMonths}. Si se cambia el intervalo, se recalcula
 * {@code nextDueAt = lastValidationAt + nuevoIntervalo} (regla consistente).</p>
 */
@Service
public class LockService {

    private static final Logger log = LoggerFactory.getLogger(LockService.class);
    public static final String LOCKED_MESSAGE = "Página bloqueada, ingrese contraseña";

    private final LockConfigRepository lockConfigRepository;
    private final AdminEventRepository adminEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final InitialCredentialProvider credentialProvider;
    private final PedidosProperties properties;
    private final SecureRandom random = new SecureRandom();

    public LockService(LockConfigRepository lockConfigRepository,
                       AdminEventRepository adminEventRepository,
                       PasswordEncoder passwordEncoder,
                       InitialCredentialProvider credentialProvider,
                       PedidosProperties properties) {
        this.lockConfigRepository = lockConfigRepository;
        this.adminEventRepository = adminEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.credentialProvider = credentialProvider;
        this.properties = properties;
    }

    // =====================================================================
    //  Inicializacion
    // =====================================================================

    /**
     * Crea la configuracion de bloqueo la primera vez (idempotente). NO sobrescribe una
     * contrasena ya cambiada: solo actua si la fila no existe.
     */
    @Transactional
    public void initializeIfNeeded() {
        if (lockConfigRepository.findById(LockConfig.SINGLETON_ID).isPresent()) {
            return;
        }
        LockConfig config = new LockConfig();
        config.setId(LockConfig.SINGLETON_ID);
        config.setModuleEnabled(properties.getSecurity().isModuleEnabled());
        config.setIntervalMonths(Math.max(1, properties.getSecurity().getIntervalMonths()));
        LocalDateTime now = LocalDateTime.now();
        config.setLastValidationAt(now);
        config.setNextDueAt(now.plusMonths(config.getIntervalMonths()));

        InitialCredentialProvider.Credential credential = credentialProvider.resolve();
        if (credential != null) {
            config.setMainPasswordHash(passwordEncoder.encode(credential.password()));
            if (credential.adminEmail() != null && !credential.adminEmail().isBlank()) {
                config.setAdminEmail(credential.adminEmail().trim());
            }
            config.setMustChangePassword(true);
            log.info("Credencial administrativa inicial cargada desde una fuente segura. "
                    + "Cámbiela antes de usar el sistema en producción.");
        } else {
            // Sin credencial configurada: se genera una aleatoria (se muestra una sola vez).
            String generated = generateRandomPassword();
            config.setMainPasswordHash(passwordEncoder.encode(generated));
            config.setMustChangePassword(true);
            log.warn("=====================================================================");
            log.warn(" NO se configuro una credencial inicial (PEDIDOS_ADMIN_PASSWORD ni");
            log.warn(" config/security.properties). Se generó una contraseña aleatoria.");
            log.warn(" Anótela y cámbiela en Administración. NO se volverá a mostrar.");
            log.warn(" Contraseña temporal generada: {}", generated);
            log.warn("=====================================================================");
        }
        lockConfigRepository.save(config);
        adminEventRepository.save(new AdminEvent(AdminEventType.AJUSTE_CONFIANZA,
                "Módulo de bloqueo inicializado. Próxima validación: " + config.getNextDueAt(), "sistema"));
    }

    // =====================================================================
    //  Estado
    // =====================================================================

    @Transactional(readOnly = true)
    public LockConfig getConfig() {
        return lockConfigRepository.findById(LockConfig.SINGLETON_ID).orElseGet(() -> {
            LockConfig c = new LockConfig();
            c.setId(LockConfig.SINGLETON_ID);
            c.setLastValidationAt(LocalDateTime.now());
            c.setNextDueAt(LocalDateTime.now().plusMonths(c.getIntervalMonths()));
            return c;
        });
    }

    /** El sistema esta bloqueado si el modulo esta activo y ya venció la fecha. */
    @Transactional(readOnly = true)
    public boolean isLocked() {
        LockConfig config = getConfig();
        if (!config.isModuleEnabled()) {
            return false;
        }
        LocalDateTime due = config.getNextDueAt();
        return due != null && LocalDateTime.now().isAfter(due);
    }

    @Transactional(readOnly = true)
    public SystemDtos.LockStatusView status() {
        LockConfig config = getConfig();
        boolean locked = isLocked();
        boolean tempActive = config.getTempPasswordHash() != null
                && (config.getTempPasswordExpiresAt() == null
                    || LocalDateTime.now().isBefore(config.getTempPasswordExpiresAt()));
        return new SystemDtos.LockStatusView(
                config.isModuleEnabled(),
                locked,
                config.getNextDueAt() != null ? config.getNextDueAt().toString() : null,
                config.getLastValidationAt() != null ? config.getLastValidationAt().toString() : null,
                config.getIntervalMonths(),
                config.getAdminEmail(),
                config.isMustChangePassword(),
                tempActive,
                config.getTempPasswordExpiresAt() != null ? config.getTempPasswordExpiresAt().toString() : null,
                locked ? LOCKED_MESSAGE : "Sistema activo"
        );
    }

    // =====================================================================
    //  Validacion / desbloqueo
    // =====================================================================

    /** Verifica una contrasena (principal o temporal valida) sin renovar el periodo. */
    @Transactional(readOnly = true)
    public boolean verifyPassword(String password) {
        return passwordMatches(getConfig(), password);
    }

    /**
     * Valida la contrasena y renueva el periodo. Funciona tanto para desbloquear como para
     * renovar antes del vencimiento.
     */
    @Transactional
    public SystemDtos.LockStatusView validateAndRenew(String password, String actor) {
        LockConfig config = lockConfigRepository.findByIdForUpdate(LockConfig.SINGLETON_ID)
                .orElseThrow(() -> new BusinessException("Módulo de bloqueo no inicializado."));
        if (!passwordMatches(config, password)) {
            adminEventRepository.save(new AdminEvent(AdminEventType.DESBLOQUEO_FALLIDO,
                    "Intento de validación con contraseña incorrecta", actor));
            throw new BusinessException("Contraseña incorrecta.");
        }
        boolean wasLocked = config.isModuleEnabled() && config.getNextDueAt() != null
                && LocalDateTime.now().isAfter(config.getNextDueAt());
        LocalDateTime now = LocalDateTime.now();
        config.setLastValidationAt(now);
        config.setNextDueAt(now.plusMonths(config.getIntervalMonths()));
        config.setUpdatedAt(now);
        lockConfigRepository.save(config);
        adminEventRepository.save(new AdminEvent(
                wasLocked ? AdminEventType.SISTEMA_DESBLOQUEADO : AdminEventType.PERIODO_RENOVADO,
                "Período renovado. Próxima validación: " + config.getNextDueAt(), actor));
        return status();
    }

    // =====================================================================
    //  Contrasenas
    // =====================================================================

    @Transactional
    public void changeMainPassword(String currentPassword, String newPassword, String confirm, String actor) {
        LockConfig config = lockConfigRepository.findByIdForUpdate(LockConfig.SINGLETON_ID)
                .orElseThrow(() -> new BusinessException("Módulo de bloqueo no inicializado."));
        if (!passwordMatches(config, currentPassword)) {
            throw new BusinessException("La contraseña actual no es correcta.");
        }
        validateNewPassword(newPassword, confirm);
        config.setMainPasswordHash(passwordEncoder.encode(newPassword));
        config.setMustChangePassword(false);
        config.setUpdatedAt(LocalDateTime.now());
        lockConfigRepository.save(config);
        adminEventRepository.save(new AdminEvent(AdminEventType.PASSWORD_CAMBIADA,
                "Contraseña principal actualizada", actor));
    }

    @Transactional
    public SystemDtos.LockStatusView setTempPassword(String password, Integer validHours, String actor) {
        if (password == null || password.length() < 6) {
            throw new BusinessException("La contraseña temporal debe tener al menos 6 caracteres.");
        }
        LockConfig config = lockConfigRepository.findByIdForUpdate(LockConfig.SINGLETON_ID)
                .orElseThrow(() -> new BusinessException("Módulo de bloqueo no inicializado."));
        config.setTempPasswordHash(passwordEncoder.encode(password));
        config.setTempPasswordExpiresAt(validHours == null || validHours <= 0
                ? null : LocalDateTime.now().plusHours(validHours));
        config.setUpdatedAt(LocalDateTime.now());
        lockConfigRepository.save(config);
        adminEventRepository.save(new AdminEvent(AdminEventType.TEMP_PASSWORD_CREADA,
                "Contraseña temporal creada"
                        + (config.getTempPasswordExpiresAt() != null
                            ? " (vence " + config.getTempPasswordExpiresAt() + ")" : " (sin vencimiento)"),
                actor));
        return status();
    }

    @Transactional
    public SystemDtos.LockStatusView clearTempPassword(String actor) {
        LockConfig config = lockConfigRepository.findByIdForUpdate(LockConfig.SINGLETON_ID)
                .orElseThrow(() -> new BusinessException("Módulo de bloqueo no inicializado."));
        config.setTempPasswordHash(null);
        config.setTempPasswordExpiresAt(null);
        config.setUpdatedAt(LocalDateTime.now());
        lockConfigRepository.save(config);
        adminEventRepository.save(new AdminEvent(AdminEventType.TEMP_PASSWORD_ELIMINADA,
                "Contraseña temporal eliminada", actor));
        return status();
    }

    // =====================================================================
    //  Configuracion
    // =====================================================================

    @Transactional
    public SystemDtos.LockStatusView updateSettings(SystemDtos.LockSettingsRequest request, String actor) {
        LockConfig config = lockConfigRepository.findByIdForUpdate(LockConfig.SINGLETON_ID)
                .orElseThrow(() -> new BusinessException("Módulo de bloqueo no inicializado."));
        LocalDateTime now = LocalDateTime.now();

        if (request.intervalMonths() != null) {
            int months = request.intervalMonths();
            if (months < 1 || months > 120) {
                throw new BusinessException("El intervalo debe estar entre 1 y 120 meses.");
            }
            config.setIntervalMonths(months);
            // Regla documentada: al cambiar el intervalo se recalcula desde la ultima validacion.
            LocalDateTime base = config.getLastValidationAt() != null ? config.getLastValidationAt() : now;
            config.setNextDueAt(base.plusMonths(months));
            adminEventRepository.save(new AdminEvent(AdminEventType.INTERVALO_CAMBIADO,
                    "Intervalo cambiado a " + months + " meses. Próxima validación: " + config.getNextDueAt(), actor));
        }
        if (request.moduleEnabled() != null) {
            config.setModuleEnabled(request.moduleEnabled());
            adminEventRepository.save(new AdminEvent(
                    request.moduleEnabled() ? AdminEventType.MODULO_ACTIVADO : AdminEventType.MODULO_DESACTIVADO,
                    "Módulo de bloqueo " + (request.moduleEnabled() ? "activado" : "desactivado"), actor));
        }
        if (request.adminEmail() != null) {
            config.setAdminEmail(request.adminEmail().isBlank() ? null : request.adminEmail().trim());
            adminEventRepository.save(new AdminEvent(AdminEventType.EMAIL_CAMBIADO,
                    "Correo administrativo actualizado", actor));
        }
        config.setUpdatedAt(now);
        lockConfigRepository.save(config);
        return status();
    }

    // =====================================================================
    //  Recuperacion (requiere acceso al servidor)
    // =====================================================================

    /**
     * Recuperacion de emergencia: restablece la fecha de vencimiento (desbloquea) y,
     * opcionalmente, restablece la contrasena desde una fuente segura. NO elimina pedidos,
     * productos ni configuraciones. Debe invocarse con acceso al servidor.
     */
    @Transactional
    public void recover(boolean resetPassword) {
        LockConfig config = lockConfigRepository.findByIdForUpdate(LockConfig.SINGLETON_ID)
                .orElseGet(() -> {
                    LockConfig c = new LockConfig();
                    c.setId(LockConfig.SINGLETON_ID);
                    return c;
                });
        LocalDateTime now = LocalDateTime.now();
        config.setLastValidationAt(now);
        config.setNextDueAt(now.plusMonths(Math.max(1, config.getIntervalMonths())));
        config.setUpdatedAt(now);
        StringBuilder desc = new StringBuilder("Recuperación ejecutada: fecha de vencimiento restablecida.");
        if (resetPassword) {
            InitialCredentialProvider.Credential credential = credentialProvider.resolve();
            if (credential == null) {
                throw new BusinessException("Se pidió restablecer la contraseña pero no hay credencial "
                        + "configurada (PEDIDOS_ADMIN_PASSWORD o config/security.properties).");
            }
            config.setMainPasswordHash(passwordEncoder.encode(credential.password()));
            config.setMustChangePassword(true);
            desc.append(" Contraseña principal restablecida desde la fuente segura.");
        }
        lockConfigRepository.save(config);
        adminEventRepository.save(new AdminEvent(AdminEventType.RECUPERACION_EJECUTADA, desc.toString(), "recuperacion"));
        log.info("Recuperación administrativa ejecutada. resetPassword={}", resetPassword);
    }

    // =====================================================================
    //  Utilidades
    // =====================================================================

    private boolean passwordMatches(LockConfig config, String password) {
        if (password == null || password.isBlank()) {
            return false;
        }
        if (config.getMainPasswordHash() != null
                && passwordEncoder.matches(password, config.getMainPasswordHash())) {
            return true;
        }
        if (config.getTempPasswordHash() != null) {
            boolean expired = config.getTempPasswordExpiresAt() != null
                    && LocalDateTime.now().isAfter(config.getTempPasswordExpiresAt());
            if (!expired && passwordEncoder.matches(password, config.getTempPasswordHash())) {
                return true;
            }
        }
        return false;
    }

    private void validateNewPassword(String newPassword, String confirm) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new BusinessException("La nueva contraseña debe tener al menos 8 caracteres.");
        }
        if (!newPassword.equals(confirm)) {
            throw new BusinessException("La confirmación no coincide con la nueva contraseña.");
        }
        // No se permite reutilizar la credencial inicial como contrasena definitiva.
        // Se compara contra la fuente segura configurada; NUNCA contra un literal en el codigo.
        InitialCredentialProvider.Credential initial = credentialProvider.resolve();
        if (initial != null && newPassword.equals(initial.password())) {
            throw new BusinessException("Por seguridad, no puede usar la credencial inicial como contraseña definitiva.");
        }
    }

    private String generateRandomPassword() {
        byte[] bytes = new byte[12];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
