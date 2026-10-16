package com.restaurante.pedidos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada de pedidos-restaurante.
 *
 * <p>Sistema de pedidos para restaurante con tres tipos de pedido (Mesa, Domicilio y
 * Para llevar), distribucion del trabajo por areas de preparacion, impresion desde el
 * computador servidor y modulo administrativo con bloqueo temporal.</p>
 *
 * <p>Se ejecuta con {@code mvn spring-boot:run} o desde IntelliJ IDEA ejecutando esta
 * clase. Escucha en {@code 0.0.0.0:8080} (configurable) para permitir el acceso desde
 * otros dispositivos de la red local.</p>
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class PedidosApplication {

    public static void main(String[] args) {
        SpringApplication.run(PedidosApplication.class, args);
    }
}
