package com.restaurante.pedidos.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controlador de vistas (Thymeleaf). Cada pagina carga su logica desde
 * {@code /js/app.js} segun el atributo {@code data-page} del cuerpo.
 */
@Controller
public class PageController {

    private void page(Model model, String title, String page) {
        model.addAttribute("title", title);
        model.addAttribute("page", page);
    }

    @GetMapping("/")
    public String index(Model model) {
        page(model, "Panel · Pedidos Restaurante", "panel");
        return "index";
    }

    @GetMapping("/mesas")
    public String mesas(Model model) {
        page(model, "Mesas y nuevo pedido · Pedidos Restaurante", "mesas");
        return "mesas";
    }

    @GetMapping("/pedido")
    public String pedido(Model model) {
        page(model, "Tomar pedido · Pedidos Restaurante", "pedido");
        return "pedido";
    }

    @GetMapping("/areas")
    public String areas(Model model) {
        page(model, "Áreas de preparación · Pedidos Restaurante", "areas");
        return "areas";
    }

    @GetMapping("/pedidos")
    public String pedidos(Model model) {
        page(model, "Pedidos · Pedidos Restaurante", "pedidos");
        return "pedidos";
    }

    @GetMapping("/catalogo")
    public String catalogo(Model model) {
        page(model, "Catálogo · Pedidos Restaurante", "catalogo");
        return "catalogo";
    }

    @GetMapping("/admin")
    public String admin(Model model) {
        page(model, "Administración · Pedidos Restaurante", "admin");
        return "admin";
    }

    @GetMapping("/impresion")
    public String impresion(Model model) {
        page(model, "Impresión · Pedidos Restaurante", "impresion");
        return "impresion";
    }

    @GetMapping("/sistema")
    public String sistema(Model model) {
        page(model, "Sistema · Pedidos Restaurante", "sistema");
        return "sistema";
    }
}
