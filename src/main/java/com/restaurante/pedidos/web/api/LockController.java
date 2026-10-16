package com.restaurante.pedidos.web.api;

import com.restaurante.pedidos.dto.SystemDtos;
import com.restaurante.pedidos.security.AttemptThrottle;
import com.restaurante.pedidos.service.AdminAuthService;
import com.restaurante.pedidos.service.LockService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Estado del modulo de bloqueo y validacion. Estas rutas permanecen accesibles aun con el
 * sistema bloqueado (son necesarias para desbloquear).
 */
@RestController
@RequestMapping("/api/lock")
public class LockController {

    private final LockService lockService;
    private final AdminAuthService adminAuthService;
    private final AttemptThrottle throttle;

    public LockController(LockService lockService,
                          AdminAuthService adminAuthService,
                          AttemptThrottle throttle) {
        this.lockService = lockService;
        this.adminAuthService = adminAuthService;
        this.throttle = throttle;
    }

    @GetMapping("/status")
    public SystemDtos.LockStatusView status() {
        return lockService.status();
    }

    /** Desbloqueo: valida la contrasena y renueva el periodo. */
    @PostMapping("/unlock")
    public SystemDtos.LockStatusView unlock(@RequestBody SystemDtos.UnlockRequest request,
                                            HttpServletRequest http) {
        String key = clientKey(http);
        if (!throttle.isAllowed(key)) {
            long secs = throttle.secondsRemaining(key);
            throw new com.restaurante.pedidos.exception.UnauthorizedException(
                    "Demasiados intentos fallidos. Espere " + secs + " segundos.");
        }
        try {
            SystemDtos.LockStatusView view = lockService.validateAndRenew(
                    request == null ? null : request.password(), key);
            throttle.recordSuccess(key);
            // Un desbloqueo correcto concede tambien acceso administrativo.
            adminAuthService.grant(http.getSession(true));
            return view;
        } catch (RuntimeException e) {
            throttle.recordFailure(key);
            throw e;
        }
    }

    /** Renovacion anticipada (misma logica que el desbloqueo). */
    @PostMapping("/renew")
    public SystemDtos.LockStatusView renew(@RequestBody SystemDtos.RenewRequest request,
                                           HttpServletRequest http) {
        String key = clientKey(http);
        if (!throttle.isAllowed(key)) {
            long secs = throttle.secondsRemaining(key);
            throw new com.restaurante.pedidos.exception.UnauthorizedException(
                    "Demasiados intentos fallidos. Espere " + secs + " segundos.");
        }
        try {
            SystemDtos.LockStatusView view = lockService.validateAndRenew(
                    request == null ? null : request.password(), key);
            throttle.recordSuccess(key);
            return view;
        } catch (RuntimeException e) {
            throttle.recordFailure(key);
            throw e;
        }
    }

    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
