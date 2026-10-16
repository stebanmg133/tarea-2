package com.restaurante.pedidos.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita los intentos de contrasena (administrativa y de desbloqueo) para dificultar
 * ataques por fuerza bruta.
 *
 * <p>Es un control por instancia (el servidor es unico en la red local). Tras 5 fallos
 * se bloquea la clave durante 5 minutos. Los intentos fallidos tambien quedan registrados
 * en el historial administrativo.</p>
 */
@Component
public class AttemptThrottle {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_TIME = Duration.ofMinutes(5);

    private record Entry(int count, Instant lockedUntil) {
    }

    private final Map<String, Entry> attempts = new ConcurrentHashMap<>();

    /** true si la clave puede intentar ahora mismo. */
    public boolean isAllowed(String key) {
        Entry entry = attempts.get(key);
        if (entry == null) {
            return true;
        }
        if (entry.lockedUntil != null && Instant.now().isBefore(entry.lockedUntil)) {
            return false;
        }
        return true;
    }

    /** Tiempo restante de bloqueo en segundos (0 si no esta bloqueado). */
    public long secondsRemaining(String key) {
        Entry entry = attempts.get(key);
        if (entry == null || entry.lockedUntil == null) {
            return 0;
        }
        long s = Duration.between(Instant.now(), entry.lockedUntil).getSeconds();
        return Math.max(0, s);
    }

    public void recordFailure(String key) {
        attempts.compute(key, (k, current) -> {
            int count = (current == null ? 0 : current.count()) + 1;
            Instant lockedUntil = count >= MAX_ATTEMPTS ? Instant.now().plus(LOCK_TIME) : null;
            return new Entry(count, lockedUntil);
        });
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }
}
