package com.restaurante.pedidos.config;

import com.restaurante.pedidos.service.LockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Procedimiento de recuperacion administrativa. Debe ejecutarse con acceso al servidor.
 *
 * <p>Se activa con la opcion de arranque {@code --pedidos.security.recover=true} o la
 * variable de entorno {@code PEDIDOS_SECURITY_RECOVER=true}. Opcionalmente,
 * {@code --pedidos.security.recover.reset-password=true} (o
 * {@code PEDIDOS_SECURITY_RECOVER_RESET_PASSWORD=true}) restablece ademas la contrasena
 * principal a partir de una fuente segura.</p>
 *
 * <p>NO elimina pedidos, productos ni configuraciones: solo restablece la fecha de
 * vencimiento (desbloquea) y, si se pide, la credencial desde {@code PEDIDOS_ADMIN_PASSWORD}
 * o {@code config/security.properties}.</p>
 */
@Component
@Order(100)
public class MaintenanceRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceRunner.class);

    private final LockService lockService;

    public MaintenanceRunner(LockService lockService) {
        this.lockService = lockService;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean recover = flag(args, "pedidos.security.recover", "PEDIDOS_SECURITY_RECOVER");
        if (recover) {
            boolean resetPassword = flag(args, "pedidos.security.recover.reset-password",
                    "PEDIDOS_SECURITY_RECOVER_RESET_PASSWORD");
            log.warn("Ejecutando RECUPERACIÓN administrativa (resetPassword={}). No se eliminan datos.",
                    resetPassword);
            lockService.recover(resetPassword);
            log.warn("Recuperación completada. El sistema quedó desbloqueado.");
        }
    }

    private boolean flag(ApplicationArguments args, String prop, String env) {
        if (args.containsOption(prop)) {
            String v = args.getOptionValues(prop) != null && !args.getOptionValues(prop).isEmpty()
                    ? args.getOptionValues(prop).get(0) : "true";
            return !"false".equalsIgnoreCase(v);
        }
        String envValue = System.getenv(env);
        return envValue != null && !"false".equalsIgnoreCase(envValue);
    }
}
