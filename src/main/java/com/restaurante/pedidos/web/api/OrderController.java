package com.restaurante.pedidos.web.api;

import com.restaurante.pedidos.dto.OrderDtos;
import com.restaurante.pedidos.service.OrderService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * API de pedidos: creacion, consulta, edicion, estados y cancelacion.
 * Es la operacion diaria de los empleados (no requiere login, salvo que el sistema este
 * bloqueado por el modulo de bloqueo temporal).
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public OrderDtos.OrderView create(@RequestBody OrderDtos.CreateOrderRequest request,
                                      @RequestParam(value = "actor", required = false) String actor) {
        return orderService.createOrder(request, actor);
    }

    @GetMapping
    public List<OrderDtos.OrderSummaryView> list(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String table,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return orderService.listOrders(code, type, status, table, active, from, to);
    }

    @GetMapping("/active")
    public List<OrderDtos.OrderSummaryView> active() {
        return orderService.listActive();
    }

    @GetMapping("/{id}")
    public OrderDtos.OrderView get(@PathVariable Long id) {
        return orderService.getById(id);
    }

    @GetMapping("/by-code/{code}")
    public OrderDtos.OrderView getByCode(@PathVariable String code) {
        return orderService.getByCode(code);
    }

    @GetMapping("/{id}/detail")
    public OrderDtos.OrderDetailView detail(@PathVariable Long id) {
        return orderService.getDetail(id);
    }

    @GetMapping("/{id}/events")
    public List<OrderDtos.EventView> events(@PathVariable Long id) {
        return orderService.getEvents(id);
    }

    @PutMapping("/{id}")
    public OrderDtos.OrderView update(@PathVariable Long id,
                                      @RequestBody OrderDtos.UpdateOrderRequest request,
                                      @RequestParam(value = "actor", required = false) String actor) {
        return orderService.updateOrder(id, request, actor);
    }

    @PostMapping("/{id}/items")
    public OrderDtos.OrderView addItem(@PathVariable Long id,
                                       @RequestBody OrderDtos.CreateItemRequest request,
                                       @RequestParam(value = "actor", required = false) String actor) {
        return orderService.addItem(id, request, actor);
    }

    @PutMapping("/{id}/items/{itemId}")
    public OrderDtos.OrderView updateItem(@PathVariable Long id,
                                          @PathVariable Long itemId,
                                          @RequestBody OrderDtos.CreateItemRequest request,
                                          @RequestParam(value = "actor", required = false) String actor) {
        return orderService.updateItem(id, itemId, request, actor);
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public OrderDtos.OrderView removeItem(@PathVariable Long id,
                                          @PathVariable Long itemId,
                                          @RequestParam(value = "actor", required = false) String actor) {
        return orderService.removeItem(id, itemId, actor);
    }

    @PostMapping("/{id}/cancel")
    public OrderDtos.OrderView cancel(@PathVariable Long id,
                                      @RequestBody(required = false) OrderDtos.CancelRequest request,
                                      @RequestParam(value = "actor", required = false) String actor) {
        String reason = request == null ? null : request.reason();
        return orderService.cancelOrder(id, reason, actor);
    }

    @PostMapping("/{id}/items/{itemId}/status")
    public OrderDtos.OrderView updateItemStatus(@PathVariable Long id,
                                                @PathVariable Long itemId,
                                                @RequestBody OrderDtos.ItemStatusRequest request,
                                                @RequestParam(value = "actor", required = false) String actor) {
        return orderService.updateItemStatus(id, itemId, request.areaId(), request.status(), actor);
    }
}
