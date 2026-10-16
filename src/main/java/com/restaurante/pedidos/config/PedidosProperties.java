package com.restaurante.pedidos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Propiedades de la aplicacion bajo el prefijo {@code pedidos}.
 * Los valores de negocio (nombre, moneda, valor de domicilio, impresora, ancho de papel)
 * se leen al inicio y luego pueden editarse en runtime desde Administracion, ya que se
 * persisten en {@code app_setting}.
 */
@ConfigurationProperties(prefix = "pedidos")
public class PedidosProperties {

    private final Business business = new Business();
    private final Seed seed = new Seed();
    private final Print print = new Print();
    private final Security security = new Security();
    private final Backup backup = new Backup();

    public Business getBusiness() {
        return business;
    }

    public Seed getSeed() {
        return seed;
    }

    public Print getPrint() {
        return print;
    }

    public Security getSecurity() {
        return security;
    }

    public Backup getBackup() {
        return backup;
    }

    public static class Business {
        private String name = "Pizzería Demo";
        private String currencySymbol = "$";
        private BigDecimal defaultDeliveryFee = new BigDecimal("5000");

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCurrencySymbol() {
            return currencySymbol;
        }

        public void setCurrencySymbol(String currencySymbol) {
            this.currencySymbol = currencySymbol;
        }

        public BigDecimal getDefaultDeliveryFee() {
            return defaultDeliveryFee;
        }

        public void setDefaultDeliveryFee(BigDecimal defaultDeliveryFee) {
            this.defaultDeliveryFee = defaultDeliveryFee;
        }
    }

    public static class Seed {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Print {
        /** SIMULATION o REAL. */
        private String mode = "SIMULATION";
        private String printerName = "";
        private int paperWidth = 42;
        private int dedupeWindowSeconds = 20;
        /** Carpeta donde se guardan las comandas en modo simulacion. */
        private String simulationDir = "data/print-simulation";

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getPrinterName() {
            return printerName;
        }

        public void setPrinterName(String printerName) {
            this.printerName = printerName;
        }

        public int getPaperWidth() {
            return paperWidth;
        }

        public void setPaperWidth(int paperWidth) {
            this.paperWidth = paperWidth;
        }

        public int getDedupeWindowSeconds() {
            return dedupeWindowSeconds;
        }

        public void setDedupeWindowSeconds(int dedupeWindowSeconds) {
            this.dedupeWindowSeconds = dedupeWindowSeconds;
        }

        public String getSimulationDir() {
            return simulationDir;
        }

        public void setSimulationDir(String simulationDir) {
            this.simulationDir = simulationDir;
        }
    }

    public static class Security {
        private int intervalMonths = 2;
        private boolean moduleEnabled = true;
        private String file = "./config/security.properties";

        public int getIntervalMonths() {
            return intervalMonths;
        }

        public void setIntervalMonths(int intervalMonths) {
            this.intervalMonths = intervalMonths;
        }

        public boolean isModuleEnabled() {
            return moduleEnabled;
        }

        public void setModuleEnabled(boolean moduleEnabled) {
            this.moduleEnabled = moduleEnabled;
        }

        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }
    }

    public static class Backup {
        private String dir = "./data/backups";

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }
    }
}
