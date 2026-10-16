package com.restaurante.pedidos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Bean de hashing de contrasenas (BCrypt). Las contrasenas nunca se guardan en texto
 * plano; se comparan mediante {@link PasswordEncoder#matches}.
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Fuerza 10 (valor por defecto recomendado de BCrypt).
        return new BCryptPasswordEncoder(10);
    }
}
