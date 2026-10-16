package com.restaurante.pedidos.web.api;

import com.restaurante.pedidos.dto.SystemDtos;
import com.restaurante.pedidos.service.AreaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Vistas de trabajo de preparacion por area. */
@RestController
@RequestMapping("/api/areas")
public class AreaController {

    private final AreaService areaService;

    public AreaController(AreaService areaService) {
        this.areaService = areaService;
    }

    /** Articulos activos (pendientes o en preparacion) del area. */
    @GetMapping("/{id}/work")
    public List<SystemDtos.AreaWorkItem> work(@PathVariable Long id) {
        return areaService.activeWork(id);
    }

    /** Todos los articulos del area en pedidos activos (incluye listos/entregados). */
    @GetMapping("/{id}/work/all")
    public List<SystemDtos.AreaWorkItem> allWork(@PathVariable Long id,
                                                 @RequestParam(defaultValue = "false") boolean includeDone) {
        return areaService.allWork(id);
    }
}
