package com.restaurante.pedidos.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.Set;

/**
 * Exige el token CSRF en toda peticion que modifica estado.
 *
 * <p>El token se obtiene en {@code GET /api/session} y se envia en la cabecera
 * {@code X-XSRF-TOKEN}. Las peticiones de solo lectura (GET, HEAD, OPTIONS) no lo
 * requieren. Esto protege, entre otros, los cambios administrativos y la creacion de
 * pedidos frente a peticiones falsificadas desde otros sitios.</p>
 */
@Component
public class CsrfInterceptor implements HandlerInterceptor {

    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final CsrfTokenService csrfTokenService;
    private final ObjectMapper objectMapper;

    public CsrfInterceptor(CsrfTokenService csrfTokenService, ObjectMapper objectMapper) {
        this.csrfTokenService = csrfTokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) {
            return true;
        }
        if (!MUTATING.contains(request.getMethod().toUpperCase())) {
            return true;
        }
        String provided = request.getHeader("X-XSRF-TOKEN");
        if (csrfTokenService.isValid(request.getSession(false), provided)) {
            return true;
        }
        response.setStatus(403);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "error", "csrf",
                "message", "Token de seguridad (CSRF) ausente o inválido. Recargue la página."));
        return false;
    }
}
