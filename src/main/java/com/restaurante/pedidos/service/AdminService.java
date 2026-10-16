package com.restaurante.pedidos.service;

import com.restaurante.pedidos.domain.AdminEvent;
import com.restaurante.pedidos.dto.SystemDtos;
import com.restaurante.pedidos.repository.AdminEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Historial administrativo y configuracion general del negocio. */
@Service
public class AdminService {

    private final AdminEventRepository adminEventRepository;
    private final SettingsService settingsService;

    public AdminService(AdminEventRepository adminEventRepository, SettingsService settingsService) {
        this.adminEventRepository = adminEventRepository;
        this.settingsService = settingsService;
    }

    @Transactional(readOnly = true)
    public List<SystemDtos.AdminEventView> events() {
        List<SystemDtos.AdminEventView> out = new ArrayList<>();
        for (AdminEvent e : adminEventRepository.findTop200ByOrderByCreatedAtDesc()) {
            out.add(new SystemDtos.AdminEventView(
                    e.getId(), e.getType().name(), e.getType().getLabel(), e.getDescription(),
                    e.getActor(), e.getCreatedAt() != null ? e.getCreatedAt().toString() : null));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public SystemDtos.SettingView settings() {
        return new SystemDtos.SettingView(
                settingsService.getBusinessName(),
                settingsService.getCurrencySymbol(),
                settingsService.getDefaultDeliveryFee());
    }

    @Transactional
    public SystemDtos.SettingView updateSettings(SystemDtos.SettingRequest request, String actor) {
        if (request.businessName() != null && !request.businessName().isBlank()) {
            settingsService.setBusinessName(request.businessName().trim());
        }
        if (request.currencySymbol() != null && !request.currencySymbol().isBlank()) {
            settingsService.setCurrencySymbol(request.currencySymbol().trim());
        }
        if (request.defaultDeliveryFee() != null && request.defaultDeliveryFee().signum() >= 0) {
            settingsService.setDefaultDeliveryFee(request.defaultDeliveryFee());
        }
        return settings();
    }
}
