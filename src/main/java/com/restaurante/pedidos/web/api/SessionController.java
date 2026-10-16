package com.restaurante.pedidos.web.api;

import com.restaurante.pedidos.dto.SystemDtos;
import com.restaurante.pedidos.security.CsrfTokenService;
import com.restaurante.pedidos.service.AdminAuthService;
import com.restaurante.pedidos.service.LockService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrega a la interfaz el token CSRF y el estado de sesion/bloqueo. Es la primera llamada
 * que hace cada pagina.
 */
@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final CsrfTokenService csrfTokenService;
    private final AdminAuthService adminAuthService;
    private final LockService lockService;

    public SessionController(CsrfTokenService csrfTokenService,
                             AdminAuthService adminAuthService,
                             LockService lockService) {
        this.csrfTokenService = csrfTokenService;
        this.adminAuthService = adminAuthService;
        this.lockService = lockService;
    }

    @GetMapping
    public SystemDtos.AdminSessionView session(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        String token = csrfTokenService.ensureToken(session);
        return new SystemDtos.AdminSessionView(
                adminAuthService.isAuthenticated(session),
                token,
                lockService.status().mustChangePassword(),
                lockService.isLocked()
        );
    }
}
