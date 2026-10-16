package com.restaurante.pedidos.web.api;

import com.restaurante.pedidos.dto.SystemDtos;
import com.restaurante.pedidos.service.TableService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Estado de las mesas M01..M15. */
@RestController
@RequestMapping("/api/tables")
public class TableController {

    private final TableService tableService;

    public TableController(TableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping
    public List<SystemDtos.TableView> list() {
        return tableService.listTables();
    }
}
