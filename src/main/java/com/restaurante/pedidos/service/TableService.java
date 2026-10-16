package com.restaurante.pedidos.service;

import com.restaurante.pedidos.domain.RestaurantTable;
import com.restaurante.pedidos.dto.SystemDtos;
import com.restaurante.pedidos.repository.RestaurantTableRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Estado de las mesas. La ocupacion real se controla en {@link OrderService} con bloqueo
 * pesimista; aqui solo se consulta y se sincroniza con los pedidos activos.
 */
@Service
public class TableService {

    private final RestaurantTableRepository tableRepository;

    public TableService(RestaurantTableRepository tableRepository) {
        this.tableRepository = tableRepository;
    }

    @Transactional(readOnly = true)
    public List<SystemDtos.TableView> listTables() {
        List<SystemDtos.TableView> out = new ArrayList<>();
        for (RestaurantTable t : tableRepository.findAllByOrderByCodeAsc()) {
            out.add(new SystemDtos.TableView(t.getId(), t.getCode(), t.isOccupied(), t.getCurrentOrderCode()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public long countOccupied() {
        return tableRepository.countByOccupiedTrue();
    }

    @Transactional(readOnly = true)
    public long countTotal() {
        return tableRepository.count();
    }
}
