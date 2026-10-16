package com.restaurante.pedidos.service;

import com.restaurante.pedidos.config.PedidosProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Obtiene la credencial administrativa inicial desde una fuente segura, en este orden:
 * <ol>
 *   <li>Variable de entorno {@code PEDIDOS_ADMIN_PASSWORD}.</li>
 *   <li>Propiedad de sistema {@code pedidos.admin.password}.</li>
 *   <li>Archivo local (excluido de git) indicado en {@code pedidos.security.file}
 *       con la propiedad {@code admin.initial-password}.</li>
 * </ol>
 *
 * <p>La credencial inicial NUNCA se escribe en el codigo fuente versionado ni se registra
 * en los logs. Si no se configura ninguna fuente, se genera una contrasena aleatoria y se
 * avisa por consola para que el administrador la cambie.</p>
 */
@Component
public class InitialCredentialProvider {

    private static final Logger log = LoggerFactory.getLogger(InitialCredentialProvider.class);

    public record Credential(String password, String adminEmail, boolean generated) {
    }

    private final PedidosProperties properties;

    public InitialCredentialProvider(PedidosProperties properties) {
        this.properties = properties;
    }

    /** Devuelve la credencial inicial, o {@code null} si no hay ninguna configurada. */
    public Credential resolve() {
        String env = System.getenv("PEDIDOS_ADMIN_PASSWORD");
        if (notBlank(env)) {
            return new Credential(env.trim(), System.getenv("PEDIDOS_ADMIN_EMAIL"), false);
        }
        String prop = System.getProperty("pedidos.admin.password");
        if (notBlank(prop)) {
            return new Credential(prop.trim(), System.getProperty("pedidos.admin.email"), false);
        }
        Properties fileProps = loadFile();
        if (fileProps != null) {
            String p = fileProps.getProperty("admin.initial-password");
            if (notBlank(p)) {
                return new Credential(p.trim(), fileProps.getProperty("admin.email"), false);
            }
        }
        return null;
    }

    private Properties loadFile() {
        String location = properties.getSecurity().getFile();
        if (location == null || location.isBlank()) {
            return null;
        }
        Path path = Path.of(location);
        if (!Files.exists(path)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(path)) {
            Properties props = new Properties();
            props.load(in);
            return props;
        } catch (IOException e) {
            log.warn("No se pudo leer el archivo de credenciales {}: {}", location, e.getMessage());
            return null;
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
