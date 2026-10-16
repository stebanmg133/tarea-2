package com.restaurante.pedidos.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.pedidos.service.LockService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * Bloquea las API de negocio cuando el modulo de bloqueo temporal esta vencido.
 *
 * <p>Solo permanecen accesibles las rutas estrictamente necesarias para desbloquear,
 * autenticar al administrador y consultar el estado del bloqueo. Asi, no es posible crear
 * ni modificar pedidos llamando directamente a la API mientras el sistema esta bloqueado,
 * aunque se manipule JavaScript o se cambie la URL.</p>
 */
@Component
public class LockInterceptor implements HandlerInterceptor {

    private final LockService lockService;
    private final ObjectMapper objectMapper;

    /** Rutas permitidas aun con el sistema bloqueado. */
    private static final String[] WHITELIST_PREFIXES = {
            "/api/session",
            "/api/lock/",
            "/api/admin/login",
            "/api/admin/logout",
            "/api/admin/session",
    };

    public LockInterceptor(LockService lockService, ObjectMapper objectMapper) {
        this.lockService = lockService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) {
            return true;
        }
        for (String prefix : WHITELIST_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        try {
            if (lockService.isLocked()) {
                response.setStatus(423); // Locked
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                objectMapper.writeValue(response.getWriter(), Map.of(
                        "error", "locked",
                        "message", LockService.LOCKED_MESSAGE));
                return false;
            }
        } catch (Exception e) {
            // Si no se puede determinar el estado, por seguridad se permite el paso solo a
            // rutas de diagnostico; el resto fallara en el controlador.
            return true;
        }
        return true;
    }
}
