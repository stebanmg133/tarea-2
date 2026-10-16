package com.restaurante.pedidos.web.api;

import com.restaurante.pedidos.dto.SystemDtos;
import com.restaurante.pedidos.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Panel operativo con informacion real del dia. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public SystemDtos.DashboardView dashboard() {
        return dashboardService.dashboard();
    }
}
