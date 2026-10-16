package com.restaurante.pedidos.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.pedidos.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * Exige autenticacion administrativa en {@code /api/admin/**}, salvo en las rutas de
 * inicio/cierre de sesion y de consulta del estado de sesion.
 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

    private final AdminAuthService adminAuthService;
    private final ObjectMapper objectMapper;

    public AdminInterceptor(AdminAuthService adminAuthService, ObjectMapper objectMapper) {
        this.adminAuthService = adminAuthService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/admin/")) {
            return true;
        }
        if (uri.equals("/api/admin/login") || uri.equals("/api/admin/logout")
                || uri.equals("/api/admin/session")) {
            return true;
        }
        HttpSession session = request.getSession(false);
        if (adminAuthService.isAuthenticated(session)) {
            return true;
        }
        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "error", "unauthorized",
                "message", "Se requiere autenticación administrativa."));
        return false;
    }
}
