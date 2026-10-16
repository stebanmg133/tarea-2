package com.restaurante.pedidos.service;

import com.restaurante.pedidos.domain.AdminEvent;
import com.restaurante.pedidos.domain.AdminEventType;
import com.restaurante.pedidos.exception.UnauthorizedException;
import com.restaurante.pedidos.repository.AdminEventRepository;
import com.restaurante.pedidos.security.AttemptThrottle;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

/**
 * Autenticacion de la seccion administrativa mediante sesion de servidor.
 *
 * <p>La contrasena administrativa es la misma credencial principal del modulo de bloqueo
 * (hash BCrypt). Tras iniciar sesion se marca la sesion del servidor; las operaciones de
 * administracion comprueban ese estado en el servidor, nunca en el cliente.</p>
 */
@Service
public class AdminAuthService {

    public static final String SESSION_ATTR = "PEDIDOS_ADMIN_AUTH";

    private final LockService lockService;
    private final AdminEventRepository adminEventRepository;
    private final AttemptThrottle throttle;

    public AdminAuthService(LockService lockService,
                            AdminEventRepository adminEventRepository,
                            AttemptThrottle throttle) {
        this.lockService = lockService;
        this.adminEventRepository = adminEventRepository;
        this.throttle = throttle;
    }

    public void login(String password, HttpSession session, String clientKey) {
        if (!throttle.isAllowed(clientKey)) {
            long secs = throttle.secondsRemaining(clientKey);
            throw new UnauthorizedException("Demasiados intentos fallidos. Espere " + secs + " segundos.");
        }
        if (password == null || !lockService.verifyPassword(password)) {
            throttle.recordFailure(clientKey);
            adminEventRepository.save(new AdminEvent(AdminEventType.ADMIN_LOGIN_FAIL,
                    "Intento de acceso administrativo fallido", clientKey));
            throw new UnauthorizedException("Contraseña incorrecta.");
        }
        throttle.recordSuccess(clientKey);
        session.setAttribute(SESSION_ATTR, Boolean.TRUE);
        adminEventRepository.save(new AdminEvent(AdminEventType.ADMIN_LOGIN_OK,
                "Acceso administrativo concedido", clientKey));
    }

    public void logout(HttpSession session, String clientKey) {
        session.removeAttribute(SESSION_ATTR);
        adminEventRepository.save(new AdminEvent(AdminEventType.ADMIN_LOGOUT,
                "Sesión administrativa cerrada", clientKey));
    }

    public boolean isAuthenticated(HttpSession session) {
        return session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_ATTR));
    }

    /** Marca la sesion como autenticada tras un desbloqueo correcto. */
    public void grant(HttpSession session) {
        if (session != null) {
            session.setAttribute(SESSION_ATTR, Boolean.TRUE);
        }
    }
}
