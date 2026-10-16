package com.restaurante.pedidos.service;

import com.restaurante.pedidos.config.PedidosProperties;
import com.restaurante.pedidos.domain.AppSetting;
import com.restaurante.pedidos.repository.AppSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuracion editable en runtime, persistida en {@code app_setting}.
 * Los valores por defecto provienen de {@code application.properties} (clase
 * {@link PedidosProperties}) y se siembran la primera vez. Cambiarlos desde
 * Administracion no sobrescribe el catalogo ni los pedidos.
 */
@Service
public class SettingsService {

    public static final String KEY_BUSINESS_NAME = "business.name";
    public static final String KEY_CURRENCY_SYMBOL = "business.currencySymbol";
    public static final String KEY_DEFAULT_DELIVERY_FEE = "business.defaultDeliveryFee";
    public static final String KEY_PRINTER_NAME = "print.printerName";
    public static final String KEY_PAPER_WIDTH = "print.paperWidth";

    private final AppSettingRepository repository;
    private final PedidosProperties properties;

    public SettingsService(AppSettingRepository repository, PedidosProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /** Siembra los valores por defecto la primera vez (idempotente). */
    @Transactional
    public void seedDefaults() {
        seedIfAbsent(KEY_BUSINESS_NAME, properties.getBusiness().getName());
        seedIfAbsent(KEY_CURRENCY_SYMBOL, properties.getBusiness().getCurrencySymbol());
        seedIfAbsent(KEY_DEFAULT_DELIVERY_FEE, properties.getBusiness().getDefaultDeliveryFee().toPlainString());
        seedIfAbsent(KEY_PRINTER_NAME, properties.getPrint().getPrinterName());
        seedIfAbsent(KEY_PAPER_WIDTH, String.valueOf(properties.getPrint().getPaperWidth()));
        normalizeLegacyBusinessName();
    }

    /**
     * Corrige la ortografía del nombre de demostración heredado ("Pizzeria Demo" ->
     * "Pizzería Demo"). Solo actúa si el valor guardado es exactamente el antiguo texto de
     * demostración, de modo que nunca cambia un nombre puesto por el negocio.
     */
    private void normalizeLegacyBusinessName() {
        repository.findById(KEY_BUSINESS_NAME).ifPresent(setting -> {
            if ("Pizzeria Demo".equals(setting.getValue())) {
                setting.setValue("Pizzería Demo");
                repository.save(setting);
            }
        });
    }

    private void seedIfAbsent(String key, String value) {
        if (repository.findById(key).isEmpty()) {
            repository.save(new AppSetting(key, value));
        }
    }

    @Transactional(readOnly = true)
    public Map<String, String> all() {
        Map<String, String> map = new HashMap<>();
        for (AppSetting s : repository.findAll()) {
            map.put(s.getKey(), s.getValue());
        }
        return map;
    }

    @Transactional(readOnly = true)
    public String get(String key, String fallback) {
        return repository.findById(key).map(AppSetting::getValue).orElse(fallback);
    }

    @Transactional
    public void set(String key, String value) {
        AppSetting setting = repository.findById(key).orElseGet(() -> new AppSetting(key, value));
        setting.setValue(value);
        repository.save(setting);
    }

    @Transactional(readOnly = true)
    public String getBusinessName() {
        return get(KEY_BUSINESS_NAME, properties.getBusiness().getName());
    }

    @Transactional
    public void setBusinessName(String name) {
        set(KEY_BUSINESS_NAME, name);
    }

    @Transactional(readOnly = true)
    public String getCurrencySymbol() {
        return get(KEY_CURRENCY_SYMBOL, properties.getBusiness().getCurrencySymbol());
    }

    @Transactional
    public void setCurrencySymbol(String symbol) {
        set(KEY_CURRENCY_SYMBOL, symbol);
    }

    @Transactional(readOnly = true)
    public BigDecimal getDefaultDeliveryFee() {
        String raw = get(KEY_DEFAULT_DELIVERY_FEE, properties.getBusiness().getDefaultDeliveryFee().toPlainString());
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return properties.getBusiness().getDefaultDeliveryFee();
        }
    }

    @Transactional
    public void setDefaultDeliveryFee(BigDecimal value) {
        set(KEY_DEFAULT_DELIVERY_FEE, value.toPlainString());
    }

    @Transactional(readOnly = true)
    public String getPrinterName() {
        return get(KEY_PRINTER_NAME, properties.getPrint().getPrinterName());
    }

    @Transactional
    public void setPrinterName(String name) {
        set(KEY_PRINTER_NAME, name == null ? "" : name);
    }

    @Transactional(readOnly = true)
    public int getPaperWidth() {
        String raw = get(KEY_PAPER_WIDTH, String.valueOf(properties.getPrint().getPaperWidth()));
        try {
            int v = Integer.parseInt(raw.trim());
            return Math.max(20, Math.min(80, v));
        } catch (NumberFormatException e) {
            return properties.getPrint().getPaperWidth();
        }
    }

    @Transactional
    public void setPaperWidth(int width) {
        set(KEY_PAPER_WIDTH, String.valueOf(Math.max(20, Math.min(80, width))));
    }

    @Transactional(readOnly = true)
    public List<AppSetting> findAll() {
        return repository.findAll();
    }
}
