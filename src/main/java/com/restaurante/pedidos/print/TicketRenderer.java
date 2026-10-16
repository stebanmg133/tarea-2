package com.restaurante.pedidos.print;

import com.restaurante.pedidos.domain.Order;
import com.restaurante.pedidos.domain.OrderItem;
import com.restaurante.pedidos.domain.OrderItemAreaTask;
import com.restaurante.pedidos.domain.PreparationArea;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Genera el texto de una comanda para impresora termica (texto plano, ancho de papel
 * configurable). Es una funcion pura y comprobable sin impresora.
 *
 * <p>Si {@code area} es nulo se imprime el pedido completo; si tiene valor, se imprime
 * unicamente lo correspondiente a esa area (sin crear pedidos independientes ni alterar
 * el total central).</p>
 */
public final class TicketRenderer {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private TicketRenderer() {
    }

    public record Context(String businessName, String currencySymbol, int paperWidth) {
    }

    public static String render(Order order, PreparationArea area, boolean reprint, Context ctx) {
        int width = Math.max(20, ctx.paperWidth());
        StringBuilder sb = new StringBuilder();

        center(sb, ctx.businessName(), width);
        if (area != null) {
            center(sb, "COMANDA DE ÁREA", width);
            center(sb, area.getName().toUpperCase(), width);
        } else {
            center(sb, "COMANDA", width);
        }
        if (reprint) {
            center(sb, "*** REIMPRESIÓN ***", width);
        }
        separator(sb, width);

        line(sb, "Pedido : " + order.getCode(), width);
        line(sb, "Fecha  : " + order.getCreatedAt().format(FMT), width);
        line(sb, "Tipo   : " + order.getType().getLabel(), width);
        if (order.getTableCode() != null) {
            line(sb, "Mesa   : " + order.getTableCode(), width);
        }
        if (order.getCustomerName() != null && !order.getCustomerName().isBlank()) {
            line(sb, "Cliente: " + order.getCustomerName(), width);
        }
        if (order.getCustomerPhone() != null && !order.getCustomerPhone().isBlank()) {
            line(sb, "Tel    : " + order.getCustomerPhone(), width);
        }
        if (order.getType() == com.restaurante.pedidos.domain.OrderType.DOMICILIO
                && order.getDeliveryAddress() != null) {
            line(sb, "Dir    : " + order.getDeliveryAddress(), width);
        }
        if (order.isNeedsReprint()) {
            line(sb, "AVISO  : comanda modificada, requiere actualización", width);
        }
        line(sb, "Versión: " + order.getContentVersion(), width);
        separator(sb, width);

        List<OrderItem> items = filterItems(order, area);
        for (OrderItem item : items) {
            wrap(sb, item.getQuantity() + "x " + item.getProductName(), width);
            StringBuilder detail = new StringBuilder();
            if (item.getVariantName() != null) {
                detail.append("   - ").append(item.getVariantName());
            }
            if (item.getFlavorName() != null) {
                detail.append(item.getVariantName() != null ? " / " : "   - ").append(item.getFlavorName());
            }
            if (detail.length() > 0) {
                wrap(sb, detail.toString(), width);
            }
            if (item.getNotes() != null && !item.getNotes().isBlank()) {
                wrap(sb, "   * " + item.getNotes(), width);
            }
        }

        separator(sb, width);
        if (area == null) {
            line(sb, "Subtotal: " + money(order.getSubtotal(), ctx.currencySymbol()), width);
            if (order.getType() == com.restaurante.pedidos.domain.OrderType.DOMICILIO) {
                line(sb, "Domicilio: " + money(order.getDeliveryFee(), ctx.currencySymbol()), width);
            }
            line(sb, "TOTAL   : " + money(order.getTotal(), ctx.currencySymbol()), width);
        } else {
            center(sb, "Solo artículos de: " + area.getName(), width);
        }

        if (order.getGeneralNotes() != null && !order.getGeneralNotes().isBlank()) {
            separator(sb, width);
            wrap(sb, "Observaciones: " + order.getGeneralNotes(), width);
        }

        separator(sb, width);
        center(sb, "Pedido " + order.getCode(), width);
        sb.append('\n');
        sb.append('\n');
        return sb.toString();
    }

    private static List<OrderItem> filterItems(Order order, PreparationArea area) {
        List<OrderItem> out = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            if (item.isCancelled()) {
                continue;
            }
            if (area == null) {
                out.add(item);
                continue;
            }
            boolean belongs = false;
            for (OrderItemAreaTask task : item.getAreaTasks()) {
                if (task.getArea().getId().equals(area.getId())) {
                    belongs = true;
                    break;
                }
            }
            if (belongs) {
                out.add(item);
            }
        }
        return out;
    }

    private static String money(BigDecimal value, String symbol) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        String plain = v.setScale(0, RoundingMode.HALF_UP).toPlainString();
        return symbol + groupThousands(plain);
    }

    private static String groupThousands(String digits) {
        boolean negative = digits.startsWith("-");
        if (negative) {
            digits = digits.substring(1);
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            sb.append(digits.charAt(i));
            count++;
            if (count % 3 == 0 && i != 0) {
                sb.append('.');
            }
        }
        if (negative) {
            sb.append('-');
        }
        return sb.reverse().toString();
    }

    private static void center(StringBuilder sb, String text, int width) {
        if (text == null) {
            return;
        }
        int len = text.length();
        if (len >= width) {
            sb.append(text).append('\n');
            return;
        }
        int pad = (width - len) / 2;
        sb.append(" ".repeat(pad)).append(text).append('\n');
    }

    private static void separator(StringBuilder sb, int width) {
        sb.append("-".repeat(width)).append('\n');
    }

    private static void line(StringBuilder sb, String text, int width) {
        wrap(sb, text, width);
    }

    private static void wrap(StringBuilder sb, String text, int width) {
        if (text == null) {
            return;
        }
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String w : words) {
            if (current.length() == 0) {
                current.append(w);
            } else if (current.length() + 1 + w.length() <= width) {
                current.append(' ').append(w);
            } else {
                sb.append(current).append('\n');
                current = new StringBuilder(w);
            }
        }
        if (current.length() > 0) {
            sb.append(current).append('\n');
        }
    }
}
