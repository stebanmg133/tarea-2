package com.restaurante.pedidos.service;

import com.restaurante.pedidos.domain.Flavor;
import com.restaurante.pedidos.domain.Order;
import com.restaurante.pedidos.domain.OrderEvent;
import com.restaurante.pedidos.domain.OrderEventType;
import com.restaurante.pedidos.domain.OrderItem;
import com.restaurante.pedidos.domain.OrderItemAreaTask;
import com.restaurante.pedidos.domain.OrderStatus;
import com.restaurante.pedidos.domain.OrderType;
import com.restaurante.pedidos.domain.PreparationArea;
import com.restaurante.pedidos.domain.Product;
import com.restaurante.pedidos.domain.ProductVariant;
import com.restaurante.pedidos.domain.RestaurantTable;
import com.restaurante.pedidos.dto.OrderDtos;
import com.restaurante.pedidos.exception.BusinessException;
import com.restaurante.pedidos.exception.NotFoundException;
import com.restaurante.pedidos.repository.OrderRepository;
import com.restaurante.pedidos.repository.OrderEventRepository;
import com.restaurante.pedidos.repository.PreparationAreaRepository;
import com.restaurante.pedidos.repository.ProductRepository;
import com.restaurante.pedidos.repository.RestaurantTableRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Nucleo de la logica de pedidos: creacion, edicion, estados, cancelacion y totales.
 *
 * <p>El total SIEMPRE se calcula en el backend a partir del catalogo; nunca se confia en
 * el total enviado por el navegador. Cada articulo guarda el precio aplicado en el
 * momento de agregarlo.</p>
 */
@Service
public class OrderService {

    private static final EnumSet<OrderStatus> ACTIVE_STATUSES =
            EnumSet.of(OrderStatus.PENDIENTE, OrderStatus.EN_PREPARACION, OrderStatus.LISTO);
    private static final EnumSet<OrderStatus> ALLOWED_TASK_STATUSES =
            EnumSet.of(OrderStatus.PENDIENTE, OrderStatus.EN_PREPARACION, OrderStatus.LISTO, OrderStatus.ENTREGADO);
    private static final String DEFAULT_AREA_CODE = "COCINA";

    private final OrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;
    private final ProductRepository productRepository;
    private final PreparationAreaRepository areaRepository;
    private final RestaurantTableRepository tableRepository;
    private final OrderNumberService orderNumberService;
    private final OrderStatusCalculator statusCalculator;
    private final OrderMapper mapper;
    private final SettingsService settingsService;

    public OrderService(OrderRepository orderRepository,
                        OrderEventRepository orderEventRepository,
                        ProductRepository productRepository,
                        PreparationAreaRepository areaRepository,
                        RestaurantTableRepository tableRepository,
                        OrderNumberService orderNumberService,
                        OrderStatusCalculator statusCalculator,
                        OrderMapper mapper,
                        SettingsService settingsService) {
        this.orderRepository = orderRepository;
        this.orderEventRepository = orderEventRepository;
        this.productRepository = productRepository;
        this.areaRepository = areaRepository;
        this.tableRepository = tableRepository;
        this.orderNumberService = orderNumberService;
        this.statusCalculator = statusCalculator;
        this.mapper = mapper;
        this.settingsService = settingsService;
    }

    // =====================================================================
    //  Creacion
    // =====================================================================

    @Transactional
    public OrderDtos.OrderView createOrder(OrderDtos.CreateOrderRequest request, String actor) {
        if (request == null || request.type() == null) {
            throw new BusinessException("Debe indicar el tipo de pedido.");
        }
        // Idempotencia: si ya existe un pedido con este token, se devuelve.
        if (request.clientToken() != null && !request.clientToken().isBlank()) {
            Optional<Order> existing = orderRepository.findByClientToken(request.clientToken().trim());
            if (existing.isPresent()) {
                return mapper.toView(existing.get());
            }
        }

        OrderType type = parseType(request.type());
        Order order = new Order(orderNumberService.nextOrderCode(), type);
        order.setCreatedBy(actor);
        order.setClientToken(blankToNull(request.clientToken()));

        RestaurantTable lockedTable = null;
        if (type == OrderType.MESA) {
            lockedTable = occupyTable(request.tableCode());
            order.setTableCode(lockedTable.getCode());
            // En mesa el nombre/telefono son informativos y opcionales.
            order.setCustomerName(trim(request.customerName()));
            order.setCustomerPhone(trim(request.customerPhone()));
        } else if (type == OrderType.DOMICILIO) {
            validateDelivery(request);
            order.setCustomerName(trim(request.customerName()));
            order.setCustomerPhone(trim(request.customerPhone()));
            order.setDeliveryAddress(trim(request.deliveryAddress()));
            BigDecimal fee = request.deliveryFee() == null ? settingsService.getDefaultDeliveryFee() : request.deliveryFee();
            if (fee.signum() < 0) {
                throw new BusinessException("El valor del domicilio no puede ser negativo.");
            }
            order.setDeliveryFee(fee.setScale(2, java.math.RoundingMode.HALF_UP));
        } else {
            // LLEVAR: datos opcionales
            order.setCustomerName(trim(request.customerName()));
            order.setCustomerPhone(trim(request.customerPhone()));
            order.setDeliveryFee(BigDecimal.ZERO.setScale(2));
        }

        if (request.generalNotes() != null) {
            order.setGeneralNotes(cap(request.generalNotes(), 500));
        }

        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException("El pedido debe tener al menos un artículo.");
        }
        for (OrderDtos.CreateItemRequest itemReq : request.items()) {
            order.addItem(buildItem(itemReq));
        }

        // Estado inicial y totales.
        for (OrderItem item : order.getItems()) {
            statusCalculator.refreshItemStatus(item);
        }
        recalcTotals(order);
        statusCalculator.refreshOrderStatus(order);

        Order saved = orderRepository.save(order);
        addEvent(saved, OrderEventType.PEDIDO_CREADO,
                "Pedido " + saved.getCode() + " creado (" + type.getLabel() + ")", actor);
        if (lockedTable != null) {
            lockedTable.setOccupied(true);
            lockedTable.setCurrentOrderCode(saved.getCode());
            tableRepository.save(lockedTable);
            addEvent(saved, OrderEventType.MESA_OCUPADA, "Mesa " + lockedTable.getCode() + " ocupada", actor);
        }
        return mapper.toView(saved);
    }

    // =====================================================================
    //  Consultas
    // =====================================================================

    @Transactional(readOnly = true)
    public OrderDtos.OrderView getById(Long id) {
        return mapper.toView(loadOrder(id));
    }

    @Transactional(readOnly = true)
    public OrderDtos.OrderView getByCode(String code) {
        Order order = orderRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("Pedido no encontrado: " + code));
        return mapper.toView(order);
    }

    @Transactional(readOnly = true)
    public OrderDtos.OrderDetailView getDetail(Long id) {
        Order order = loadOrder(id);
        return new OrderDtos.OrderDetailView(mapper.toView(order), getEvents(id));
    }

    @Transactional(readOnly = true)
    public List<OrderDtos.OrderSummaryView> listOrders(String code, String type, String status, String tableCode,
                                                       Boolean activeOnly, LocalDate from, LocalDate to) {
        Specification<Order> spec = Specification.allOf();
        if (code != null && !code.isBlank()) {
            String like = "%" + code.trim().toUpperCase(Locale.ROOT) + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.upper(root.get("code")), like));
        }
        if (type != null && !type.isBlank()) {
            OrderType t = parseType(type);
            spec = spec.and((root, q, cb) -> cb.equal(root.get("type"), t));
        }
        if (status != null && !status.isBlank()) {
            OrderStatus s = parseStatus(status);
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), s));
        }
        if (tableCode != null && !tableCode.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("tableCode"), tableCode.trim().toUpperCase(Locale.ROOT)));
        }
        if (Boolean.TRUE.equals(activeOnly)) {
            spec = spec.and((root, q, cb) -> root.get("status").in(ACTIVE_STATUSES));
        }
        if (from != null) {
            LocalDateTime start = from.atStartOfDay();
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start));
        }
        if (to != null) {
            LocalDateTime end = to.plusDays(1).atStartOfDay().minusNanos(1);
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), end));
        }
        spec = spec.and((root, q, cb) -> {
            q.orderBy(cb.desc(root.get("createdAt")));
            return cb.conjunction();
        });
        List<OrderDtos.OrderSummaryView> out = new ArrayList<>();
        for (Order o : orderRepository.findAll(spec)) {
            out.add(mapper.toSummary(o));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<OrderDtos.OrderSummaryView> listActive() {
        List<OrderDtos.OrderSummaryView> out = new ArrayList<>();
        for (Order o : orderRepository.findByStatusInOrderByCreatedAtAsc(new ArrayList<>(ACTIVE_STATUSES))) {
            out.add(mapper.toSummary(o));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<OrderDtos.EventView> getEvents(Long orderId) {
        List<OrderDtos.EventView> out = new ArrayList<>();
        if (!orderRepository.existsById(orderId)) {
            throw new NotFoundException("Pedido no encontrado: id " + orderId);
        }
        for (OrderEvent e : orderEventRepository.findByOrderIdOrderByCreatedAtAsc(orderId)) {
            out.add(mapper.toEventView(e));
        }
        return out;
    }

    // =====================================================================
    //  Edicion
    // =====================================================================

    @Transactional
    public OrderDtos.OrderView updateOrder(Long orderId, OrderDtos.UpdateOrderRequest req, String actor) {
        Order order = loadOrder(orderId);
        ensureNotCancelled(order);

        if (order.getType() == OrderType.MESA) {
            // Los datos de cliente en mesa son informativos y opcionales.
            order.setCustomerName(trim(req.customerName()));
            order.setCustomerPhone(trim(req.customerPhone()));
        } else if (order.getType() == OrderType.DOMICILIO) {
            if (blank(req.customerName()) || blank(req.customerPhone()) || blank(req.deliveryAddress())) {
                throw new BusinessException("En domicilio se requieren nombre, teléfono y dirección.");
            }
            order.setCustomerName(trim(req.customerName()));
            order.setCustomerPhone(trim(req.customerPhone()));
            order.setDeliveryAddress(trim(req.deliveryAddress()));
            BigDecimal fee = req.deliveryFee() == null ? order.getDeliveryFee() : req.deliveryFee();
            if (fee.signum() < 0) {
                throw new BusinessException("El valor del domicilio no puede ser negativo.");
            }
            order.setDeliveryFee(fee.setScale(2, java.math.RoundingMode.HALF_UP));
        } else {
            order.setCustomerName(trim(req.customerName()));
            order.setCustomerPhone(trim(req.customerPhone()));
        }
        order.setGeneralNotes(req.generalNotes() == null ? null : cap(req.generalNotes(), 500));

        touchContent(order);
        recalcTotals(order);
        Order saved = orderRepository.save(order);
        addEvent(saved, OrderEventType.PEDIDO_MODIFICADO, "Datos generales del pedido actualizados", actor);
        return mapper.toView(saved);
    }

    @Transactional
    public OrderDtos.OrderView addItem(Long orderId, OrderDtos.CreateItemRequest req, String actor) {
        Order order = loadOrder(orderId);
        ensureNotCancelled(order);
        OrderItem item = buildItem(req);
        order.addItem(item);
        statusCalculator.refreshItemStatus(item);
        touchContent(order);
        recalcTotals(order);
        statusCalculator.refreshOrderStatus(order);
        Order saved = orderRepository.save(order);
        addEvent(saved, OrderEventType.ARTICULO_AGREGADO,
                "Agregado: " + item.getQuantity() + "x " + item.getProductName()
                        + describeVariant(item), actor);
        return mapper.toView(saved);
    }

    @Transactional
    public OrderDtos.OrderView updateItem(Long orderId, Long itemId, OrderDtos.CreateItemRequest req, String actor) {
        Order order = loadOrder(orderId);
        ensureNotCancelled(order);
        OrderItem item = findItem(order, itemId);
        if (item.isCancelled()) {
            throw new BusinessException("El artículo está cancelado y no puede modificarse.");
        }
        Integer qty = req.quantity();
        if (qty != null) {
            if (qty < 1) {
                throw new BusinessException("La cantidad debe ser al menos 1.");
            }
            item.setQuantity(qty);
        }
        // Cambio de variante / sabor: se resuelve de nuevo contra el catalogo.
        if (req.productId() != null && item.getProduct() != null
                && !req.productId().equals(item.getProduct().getId())) {
            throw new BusinessException("No se puede cambiar el producto de un artículo existente. Retírelo y agregue otro.");
        }
        if (item.getProduct() != null) {
            // Si no se envia variante/sabor, se conservan los actuales (no se borran).
            String variant = req.variant() != null ? req.variant() : item.getVariantName();
            String flavor = req.flavor() != null ? req.flavor() : item.getFlavorName();
            applyVariantAndFlavor(item, item.getProduct(), variant, flavor);
        }
        // null = no tocar las observaciones; cadena vacia = borrarlas.
        if (req.notes() != null) {
            item.setNotes(req.notes().isBlank() ? null : cap(req.notes(), 400));
        }
        statusCalculator.refreshItemStatus(item);
        touchContent(order);
        recalcTotals(order);
        statusCalculator.refreshOrderStatus(order);
        Order saved = orderRepository.save(order);
        addEvent(saved, OrderEventType.ARTICULO_ACTUALIZADO,
                "Artículo actualizado: " + item.getQuantity() + "x " + item.getProductName(), actor);
        return mapper.toView(saved);
    }

    @Transactional
    public OrderDtos.OrderView removeItem(Long orderId, Long itemId, String actor) {
        Order order = loadOrder(orderId);
        ensureNotCancelled(order);
        OrderItem item = findItem(order, itemId);
        if (item.isCancelled()) {
            throw new BusinessException("El artículo ya estaba retirado.");
        }
        item.setCancelled(true);
        item.setStatus(OrderStatus.CANCELADO);
        for (OrderItemAreaTask task : item.getAreaTasks()) {
            task.setStatus(OrderStatus.CANCELADO);
        }
        touchContent(order);
        recalcTotals(order);
        statusCalculator.refreshOrderStatus(order);
        Order saved = orderRepository.save(order);
        addEvent(saved, OrderEventType.ARTICULO_ELIMINADO,
                "Retirado: " + item.getProductName() + describeVariant(item), actor);
        maybeFinalize(saved, actor);
        return mapper.toView(saved);
    }

    @Transactional
    public OrderDtos.OrderView cancelOrder(Long orderId, String reason, String actor) {
        Order order = loadOrder(orderId);
        if (order.getStatus() == OrderStatus.CANCELADO) {
            throw new BusinessException("El pedido ya está cancelado.");
        }
        order.setStatus(OrderStatus.CANCELADO);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelReason(reason == null || reason.isBlank() ? "Sin motivo indicado" : cap(reason, 255));
        releaseTableIfAny(order, actor);
        Order saved = orderRepository.save(order);
        addEvent(saved, OrderEventType.PEDIDO_CANCELADO, "Pedido cancelado: " + saved.getCancelReason(), actor);
        return mapper.toView(saved);
    }

    // =====================================================================
    //  Estados
    // =====================================================================

    @Transactional
    public OrderDtos.OrderView updateItemStatus(Long orderId, Long itemId, Long areaId, String statusName, String actor) {
        Order order = loadOrder(orderId);
        if (order.getStatus() == OrderStatus.CANCELADO) {
            throw new BusinessException("El pedido está cancelado.");
        }
        OrderItem item = findItem(order, itemId);
        if (item.isCancelled()) {
            throw new BusinessException("El artículo está cancelado.");
        }
        OrderStatus newStatus = parseStatus(statusName);
        if (!ALLOWED_TASK_STATUSES.contains(newStatus)) {
            throw new BusinessException("Estado no permitido para una tarea: " + statusName);
        }

        boolean updated = false;
        for (OrderItemAreaTask task : item.getAreaTasks()) {
            if (areaId == null || task.getArea().getId().equals(areaId)) {
                task.setStatus(newStatus);
                updated = true;
            }
        }
        if (!updated) {
            throw new BusinessException("No se encontró la tarea del área indicada para este artículo.");
        }
        statusCalculator.refreshItemStatus(item);
        statusCalculator.refreshOrderStatus(order);
        Order saved = orderRepository.save(order);
        addEvent(saved, OrderEventType.ESTADO_ARTICULO_ACTUALIZADO,
                item.getProductName() + " -> " + newStatus.getLabel()
                        + (areaId == null ? "" : " (area id " + areaId + ")"), actor);
        maybeFinalize(saved, actor);
        return mapper.toView(saved);
    }

    // =====================================================================
    //  Internos
    // =====================================================================

    private Order loadOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Pedido no encontrado: id " + id));
    }

    private OrderItem findItem(Order order, Long itemId) {
        for (OrderItem item : order.getItems()) {
            if (item.getId() != null && item.getId().equals(itemId)) {
                return item;
            }
        }
        throw new NotFoundException("Artículo no encontrado en el pedido: id " + itemId);
    }

    private OrderItem buildItem(OrderDtos.CreateItemRequest req) {
        if (req == null || req.productId() == null) {
            throw new BusinessException("Cada artículo debe referenciar un producto válido.");
        }
        Product product = productRepository.findById(req.productId())
                .orElseThrow(() -> new BusinessException("Producto no encontrado: id " + req.productId()));
        if (!product.isActive()) {
            throw new BusinessException("El producto '" + product.getName() + "' está inactivo y no se puede agregar.");
        }
        int qty = req.quantity() == null ? 1 : req.quantity();
        if (qty < 1) {
            throw new BusinessException("La cantidad debe ser al menos 1.");
        }
        OrderItem item = new OrderItem(product, product.getName(), BigDecimal.ZERO, qty);
        item.setCategoryName(product.getCategory() != null ? product.getCategory().getName() : null);
        applyVariantAndFlavor(item, product, req.variant(), req.flavor());
        item.setNotes(req.notes() == null ? null : cap(req.notes(), 400));

        // Destinos (tareas por area). Si el producto no tiene areas, se usa el area por defecto.
        List<PreparationArea> areas = new ArrayList<>(product.getAreas());
        if (areas.isEmpty()) {
            PreparationArea fallback = areaRepository.findByCode(DEFAULT_AREA_CODE)
                    .orElseThrow(() -> new BusinessException(
                            "El producto '" + product.getName() + "' no tiene área y no existe el área por defecto."));
            areas.add(fallback);
        }
        for (PreparationArea area : areas) {
            item.addAreaTask(new OrderItemAreaTask(area));
        }
        return item;
    }

    private void applyVariantAndFlavor(OrderItem item, Product product, String variantName, String flavorName) {
        if (product == null) {
            return;
        }
        BigDecimal price;
        if (product.hasVariants()) {
            if (blank(variantName)) {
                throw new BusinessException("El producto '" + product.getName() + "' requiere elegir un tamaño/variante.");
            }
            ProductVariant variant = product.getVariants().stream()
                    .filter(v -> v.getName().equalsIgnoreCase(variantName.trim()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(
                            "Variante no válida para '" + product.getName() + "': " + variantName));
            if (!variant.isActive()) {
                throw new BusinessException("La variante '" + variant.getName() + "' no está disponible.");
            }
            price = variant.getPrice();
            item.setVariantName(variant.getName());
        } else {
            price = product.getBasePrice();
            item.setVariantName(null);
        }

        if (product.hasFlavors()) {
            if (blank(flavorName)) {
                if (product.isRequiresFlavor()) {
                    throw new BusinessException("El producto '" + product.getName() + "' requiere elegir un sabor.");
                }
                item.setFlavorName(null);
            } else {
                Flavor flavor = product.getFlavors().stream()
                        .filter(f -> f.getName().equalsIgnoreCase(flavorName.trim()))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(
                                "Sabor no válido para '" + product.getName() + "': " + flavorName));
                price = price.add(flavor.getPriceDelta());
                item.setFlavorName(flavor.getName());
            }
        } else {
            item.setFlavorName(null);
        }
        item.setUnitPrice(price.setScale(2, java.math.RoundingMode.HALF_UP));
    }

    private void recalcTotals(Order order) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItem item : order.getItems()) {
            if (!item.isCancelled()) {
                subtotal = subtotal.add(item.lineTotal());
            }
        }
        subtotal = subtotal.setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal deliveryFee = order.getType() == OrderType.DOMICILIO
                ? (order.getDeliveryFee() == null ? BigDecimal.ZERO : order.getDeliveryFee())
                : BigDecimal.ZERO.setScale(2);
        order.setSubtotal(subtotal);
        order.setDeliveryFee(deliveryFee.setScale(2, java.math.RoundingMode.HALF_UP));
        order.setTotal(subtotal.add(order.getDeliveryFee()).setScale(2, java.math.RoundingMode.HALF_UP));
        order.setUpdatedAt(LocalDateTime.now());
    }

    /** Marca que el contenido cambio; si ya se imprimio, exige reimpresion. */
    private void touchContent(Order order) {
        order.setContentVersion(order.getContentVersion() + 1);
        order.setUpdatedAt(LocalDateTime.now());
        if (order.getLastPrintedVersion() != null) {
            order.setNeedsReprint(true);
        }
    }

    /** Si el pedido queda completamente entregado, fija deliveredAt y libera la mesa. */
    private void maybeFinalize(Order order, String actor) {
        if (order.getStatus() == OrderStatus.ENTREGADO && order.getDeliveredAt() == null) {
            order.setDeliveredAt(LocalDateTime.now());
            addEvent(order, OrderEventType.PEDIDO_ENTREGADO, "Pedido entregado por completo", actor);
            releaseTableIfAny(order, actor);
            orderRepository.save(order);
        }
    }

    private void releaseTableIfAny(Order order, String actor) {
        if (order.getType() != OrderType.MESA || order.getTableCode() == null) {
            return;
        }
        tableRepository.findByCode(order.getTableCode()).ifPresent(table -> {
            table.setOccupied(false);
            table.setCurrentOrderCode(null);
            tableRepository.save(table);
            addEvent(order, OrderEventType.MESA_LIBERADA, "Mesa " + table.getCode() + " liberada", actor);
        });
    }

    private RestaurantTable occupyTable(String tableCode) {
        if (blank(tableCode)) {
            throw new BusinessException("Debe seleccionar una mesa.");
        }
        String code = tableCode.trim().toUpperCase(Locale.ROOT);
        RestaurantTable table = tableRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> new BusinessException("La mesa no existe: " + code));
        if (table.isOccupied()) {
            throw new BusinessException("La mesa " + code + " ya está ocupada por el pedido "
                    + table.getCurrentOrderCode() + ".");
        }
        return table;
    }

    private void validateDelivery(OrderDtos.CreateOrderRequest request) {
        if (blank(request.customerName())) {
            throw new BusinessException("En domicilio el nombre del cliente es obligatorio.");
        }
        if (blank(request.customerPhone())) {
            throw new BusinessException("En domicilio el teléfono del cliente es obligatorio.");
        }
        if (blank(request.deliveryAddress())) {
            throw new BusinessException("En domicilio la dirección es obligatoria.");
        }
    }

    private void ensureNotCancelled(Order order) {
        if (order.getStatus() == OrderStatus.CANCELADO) {
            throw new BusinessException("El pedido está cancelado y no admite cambios.");
        }
    }

    private void addEvent(Order order, OrderEventType type, String description, String actor) {
        order.getEvents().add(new OrderEvent(order, type, cap(description, 400), actor));
    }

    private OrderType parseType(String value) {
        try {
            return OrderType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new BusinessException("Tipo de pedido no válido: " + value);
        }
    }

    private OrderStatus parseStatus(String value) {
        try {
            return OrderStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new BusinessException("Estado no válido: " + value);
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private static String blankToNull(String s) {
        return blank(s) ? null : s.trim();
    }

    private static String cap(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static String describeVariant(OrderItem item) {
        StringBuilder sb = new StringBuilder();
        if (item.getVariantName() != null) {
            sb.append(" (").append(item.getVariantName());
            if (item.getFlavorName() != null) {
                sb.append(", ").append(item.getFlavorName());
            }
            sb.append(')');
        } else if (item.getFlavorName() != null) {
            sb.append(" (").append(item.getFlavorName()).append(')');
        }
        return sb.toString();
    }

    public static EnumSet<OrderStatus> activeStatuses() {
        return ACTIVE_STATUSES;
    }
}
