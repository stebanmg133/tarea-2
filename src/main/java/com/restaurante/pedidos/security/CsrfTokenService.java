package com.restaurante.pedidos.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Gestiona el token CSRF ligado a la sesion.
 *
 * <p>El token se entrega unicamente al JavaScript del mismo origen mediante
 * {@code GET /api/session} y se exige en la cabecera {@code X-XSRF-TOKEN} para toda
 * peticion que modifica estado. Un sitio externo no puede leer el token (politica de mismo
 * origen), por lo que no puede falsificar peticiones validas.</p>
 */
@Component
public class CsrfTokenService {

    public static final String SESSION_ATTR = "PEDIDOS_CSRF_TOKEN";

    private final SecureRandom random = new SecureRandom();

    public String ensureToken(HttpSession session) {
        Object current = session.getAttribute(SESSION_ATTR);
        if (current instanceof String token && !token.isBlank()) {
            return token;
        }
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(SESSION_ATTR, token);
        return token;
    }

    public String getToken(HttpSession session) {
        Object current = session == null ? null : session.getAttribute(SESSION_ATTR);
        return current instanceof String ? (String) current : null;
    }

    public boolean isValid(HttpSession session, String provided) {
        String expected = getToken(session);
        if (expected == null || provided == null) {
            return false;
        }
        // Comparacion en tiempo constante para no filtrar informacion.
        return java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                provided.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
