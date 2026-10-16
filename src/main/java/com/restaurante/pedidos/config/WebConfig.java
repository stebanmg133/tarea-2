package com.restaurante.pedidos.config;

import com.restaurante.pedidos.security.AdminInterceptor;
import com.restaurante.pedidos.security.CsrfInterceptor;
import com.restaurante.pedidos.security.LockInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra los interceptores de seguridad en este orden:
 * <ol>
 *   <li>CSRF (valida el token en peticiones que modifican estado);</li>
 *   <li>bloqueo temporal (impide operar con el sistema bloqueado);</li>
 *   <li>autenticacion administrativa.</li>
 * </ol>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CsrfInterceptor csrfInterceptor;
    private final LockInterceptor lockInterceptor;
    private final AdminInterceptor adminInterceptor;

    public WebConfig(CsrfInterceptor csrfInterceptor,
                     LockInterceptor lockInterceptor,
                     AdminInterceptor adminInterceptor) {
        this.csrfInterceptor = csrfInterceptor;
        this.lockInterceptor = lockInterceptor;
        this.adminInterceptor = adminInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(csrfInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(lockInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(adminInterceptor).addPathPatterns("/api/admin/**");
    }
}
