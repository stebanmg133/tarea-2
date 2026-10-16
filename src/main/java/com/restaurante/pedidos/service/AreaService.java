package com.restaurante.pedidos.service;

import com.restaurante.pedidos.domain.OrderItemAreaTask;
import com.restaurante.pedidos.domain.OrderStatus;
import com.restaurante.pedidos.dto.SystemDtos;
import com.restaurante.pedidos.repository.OrderItemAreaTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Vista de trabajo por area de preparacion.
 *
 * <p>Un pedido se reparte entre areas como vistas de trabajo de un unico pedido central.
 * Cuando una tarea esta LISTO o ENTREGADO desaparece de la vista activa, pero el pedido
 * y su historial se conservan.</p>
 */
@Service
public class AreaService {

    private static final EnumSet<OrderStatus> ACTIVE_ORDER_STATUSES =
            EnumSet.of(OrderStatus.PENDIENTE, OrderStatus.EN_PREPARACION, OrderStatus.LISTO);
    private static final EnumSet<OrderStatus> WORK_TASK_STATUSES =
            EnumSet.of(OrderStatus.PENDIENTE, OrderStatus.EN_PREPARACION);

    private final OrderItemAreaTaskRepository taskRepository;

    public AreaService(OrderItemAreaTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public List<SystemDtos.AreaWorkItem> activeWork(Long areaId) {
        List<OrderItemAreaTask> tasks = taskRepository.findActiveWorkByArea(
                areaId, new ArrayList<>(ACTIVE_ORDER_STATUSES), new ArrayList<>(WORK_TASK_STATUSES));
        List<SystemDtos.AreaWorkItem> out = new ArrayList<>();
        for (OrderItemAreaTask t : tasks) {
            out.add(new SystemDtos.AreaWorkItem(
                    t.getId(),
                    t.getOrderItem().getOrder().getId(),
                    t.getOrderItem().getOrder().getCode(),
                    t.getOrderItem().getOrder().getType().name(),
                    t.getOrderItem().getOrder().getType().getLabel(),
                    t.getOrderItem().getOrder().getTableCode(),
                    t.getOrderItem().getOrder().getCustomerName(),
                    t.getOrderItem().getProductName(),
                    t.getOrderItem().getQuantity(),
                    t.getOrderItem().getFlavorName(),
                    t.getOrderItem().getVariantName(),
                    t.getOrderItem().getNotes(),
                    t.getOrderItem().getOrder().getGeneralNotes(),
                    t.getOrderItem().getStatus().name(),
                    t.getStatus().name(),
                    t.getOrderItem().getOrder().getCreatedAt().toString()
            ));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<SystemDtos.AreaWorkItem> allWork(Long areaId) {
        List<OrderStatus> all = new ArrayList<>(EnumSet.allOf(OrderStatus.class));
        List<OrderItemAreaTask> tasks = taskRepository.findActiveWorkByArea(
                areaId, new ArrayList<>(ACTIVE_ORDER_STATUSES), all);
        List<SystemDtos.AreaWorkItem> out = new ArrayList<>();
        for (OrderItemAreaTask t : tasks) {
            out.add(new SystemDtos.AreaWorkItem(
                    t.getId(),
                    t.getOrderItem().getOrder().getId(),
                    t.getOrderItem().getOrder().getCode(),
                    t.getOrderItem().getOrder().getType().name(),
                    t.getOrderItem().getOrder().getType().getLabel(),
                    t.getOrderItem().getOrder().getTableCode(),
                    t.getOrderItem().getOrder().getCustomerName(),
                    t.getOrderItem().getProductName(),
                    t.getOrderItem().getQuantity(),
                    t.getOrderItem().getFlavorName(),
                    t.getOrderItem().getVariantName(),
                    t.getOrderItem().getNotes(),
                    t.getOrderItem().getOrder().getGeneralNotes(),
                    t.getOrderItem().getStatus().name(),
                    t.getStatus().name(),
                    t.getOrderItem().getOrder().getCreatedAt().toString()
            ));
        }
        return out;
    }
}
