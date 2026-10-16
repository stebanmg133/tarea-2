package com.restaurante.pedidos.config;

import com.restaurante.pedidos.domain.Category;
import com.restaurante.pedidos.domain.Flavor;
import com.restaurante.pedidos.domain.OrderSequence;
import com.restaurante.pedidos.domain.PreparationArea;
import com.restaurante.pedidos.domain.Product;
import com.restaurante.pedidos.domain.ProductVariant;
import com.restaurante.pedidos.domain.RestaurantTable;
import com.restaurante.pedidos.repository.CategoryRepository;
import com.restaurante.pedidos.repository.OrderSequenceRepository;
import com.restaurante.pedidos.repository.PreparationAreaRepository;
import com.restaurante.pedidos.repository.ProductRepository;
import com.restaurante.pedidos.repository.RestaurantTableRepository;
import com.restaurante.pedidos.service.LockService;
import com.restaurante.pedidos.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Carga inicial <b>idempotente</b>:
 * <ul>
 *   <li>areas de preparacion y categorias: se crean solo si faltan (por codigo);</li>
 *   <li>mesas M01..M15: se crean solo si faltan;</li>
 *   <li>catalogo de demostracion: se crea <b>solo si la tabla de productos esta vacia</b>,
 *       de modo que reiniciar no duplica ni sobrescribe los cambios del usuario;</li>
 *   <li>configuracion del negocio y del modulo de bloqueo.</li>
 * </ul>
 *
 * <p>Los precios de demostracion son de ejemplo, editables, y no representan precios reales
 * del negocio.</p>
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final PreparationAreaRepository areaRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final RestaurantTableRepository tableRepository;
    private final OrderSequenceRepository orderSequenceRepository;
    private final SettingsService settingsService;
    private final LockService lockService;
    private final PedidosProperties properties;

    public DataInitializer(PreparationAreaRepository areaRepository,
                           CategoryRepository categoryRepository,
                           ProductRepository productRepository,
                           RestaurantTableRepository tableRepository,
                           OrderSequenceRepository orderSequenceRepository,
                           SettingsService settingsService,
                           LockService lockService,
                           PedidosProperties properties) {
        this.areaRepository = areaRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.tableRepository = tableRepository;
        this.orderSequenceRepository = orderSequenceRepository;
        this.settingsService = settingsService;
        this.lockService = lockService;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Infraestructura: la secuencia de pedidos debe existir SIEMPRE antes de crear pedidos,
        // para que el bloqueo pesimista (SELECT ... FOR UPDATE) encuentre la fila y no haya
        // carrera al insertarla entre varios dispositivos a la vez.
        ensureOrderSequence();

        if (!properties.getSeed().isEnabled()) {
            log.info("Carga inicial deshabilitada (pedidos.seed.enabled=false).");
            return;
        }
        settingsService.seedDefaults();
        lockService.initializeIfNeeded();

        Map<String, PreparationArea> areas = seedAreas();
        Map<String, Category> categories = seedCategories();
        seedTables();

        if (productRepository.count() == 0) {
            seedProducts(areas, categories);
            log.info("Catálogo de demostración creado (precios de ejemplo, editables).");
        } else {
            log.info("El catálogo ya contiene productos: no se sobrescribe.");
        }
        normalizeLegacyDemoNames();
        log.info("Carga inicial completada.");
    }

    /**
     * Corrige nombres de demostración heredados que usan palabras inapropiadas en español
     * de Colombia ("Calzones" -> "Panzerottis"). Solo cambia los textos exactos de
     * demostración, de modo que nunca altera nombres puestos por el negocio.
     */
    private void normalizeLegacyDemoNames() {
        areaRepository.findByCode("PIZZAS").ifPresent(area -> {
            if ("Pizzas y Calzones".equals(area.getName())) {
                area.setName("Pizzas y Panzerottis");
                areaRepository.save(area);
            }
        });
        categoryRepository.findByCode("CALZONES").ifPresent(category -> {
            if ("Calzones".equals(category.getName())) {
                category.setName("Panzerottis");
                categoryRepository.save(category);
            }
        });
        for (Product product : productRepository.findAll()) {
            if ("Calzone".equals(product.getName())) {
                product.setName("Panzerotti");
                productRepository.save(product);
            }
        }
    }

    /** Crea (si falta) la fila unica de la secuencia de pedidos. Idempotente. */
    private void ensureOrderSequence() {
        if (orderSequenceRepository.findById(OrderSequence.SINGLETON_ID).isEmpty()) {
            orderSequenceRepository.save(new OrderSequence(OrderSequence.SINGLETON_ID));
            log.info("Secuencia de pedidos inicializada.");
        }
    }

    private Map<String, PreparationArea> seedAreas() {
        Map<String, PreparationArea> map = new HashMap<>();
        Object[][] data = {
                {"PIZZAS", "Pizzas y Panzerottis", 1},
                {"COCINA", "Cocina general", 2},
                {"GASEOSAS", "Gaseosas", 3},
                {"COMIDAS", "Comidas", 4},
                {"BEBIDAS", "Bebidas", 5},
        };
        for (Object[] row : data) {
            String code = (String) row[0];
            PreparationArea area = areaRepository.findByCode(code).orElseGet(() ->
                    areaRepository.save(new PreparationArea(code, (String) row[1], (Integer) row[2])));
            map.put(code, area);
        }
        return map;
    }

    private Map<String, Category> seedCategories() {
        Map<String, Category> map = new HashMap<>();
        Object[][] data = {
                {"COMIDAS", "Comidas", true, 1},
                {"BEBIDAS", "Bebidas", true, 2},
                {"PIZZAS", "Pizzas", true, 3},
                {"GASEOSAS", "Gaseosas", true, 4},
                {"CALZONES", "Panzerottis", false, 5},
                {"JUGOS", "Jugos", false, 6},
                {"LIMONADAS", "Limonadas", false, 7},
                {"MILO", "Milo", false, 8},
                {"LASANAS", "Lasagnas", false, 9},
        };
        for (Object[] row : data) {
            String code = (String) row[0];
            Category category = categoryRepository.findByCode(code).orElseGet(() ->
                    categoryRepository.save(new Category(code, (String) row[1], (Boolean) row[2], (Integer) row[3])));
            map.put(code, category);
        }
        return map;
    }

    private void seedTables() {
        for (int i = 1; i <= 15; i++) {
            String code = String.format("M%02d", i);
            if (tableRepository.findByCode(code).isEmpty()) {
                tableRepository.save(new RestaurantTable(code));
            }
        }
    }

    private void seedProducts(Map<String, PreparationArea> areas, Map<String, Category> categories) {
        // --- Pizzas: sabores obligatorios y tamanos ---
        Product pizza = new Product("Pizza", categories.get("PIZZAS"), money("18000"));
        pizza.setRequiresFlavor(true);
        pizza.setDescription("Pizza artesanal. Precio de ejemplo.");
        pizza.setSortOrder(1);
        pizza.addVariant(new ProductVariant("Personal", money("18000"), 1));
        pizza.addVariant(new ProductVariant("Mediana", money("32000"), 2));
        pizza.addVariant(new ProductVariant("Familiar", money("45000"), 3));
        pizza.addFlavor(new Flavor("Hawaiana", money("0"), 1));
        pizza.addFlavor(new Flavor("Pepperoni", money("2000"), 2));
        pizza.addFlavor(new Flavor("Jamón y queso", money("0"), 3));
        pizza.addFlavor(new Flavor("Carnes", money("4000"), 4));
        pizza.addFlavor(new Flavor("Vegetariana", money("0"), 5));
        pizza.getAreas().add(areas.get("PIZZAS"));
        productRepository.save(pizza);

        // --- Panzerottis ---
        Product panzerotti = new Product("Panzerotti", categories.get("CALZONES"), money("15000"));
        panzerotti.addFlavor(new Flavor("Mixto", money("0"), 1));
        panzerotti.addFlavor(new Flavor("Hawaiano", money("1000"), 2));
        panzerotti.addFlavor(new Flavor("Pollo", money("1500"), 3));
        panzerotti.getAreas().add(areas.get("PIZZAS"));
        panzerotti.setSortOrder(2);
        productRepository.save(panzerotti);

        // --- Gaseosas (variantes, sin sabores) ---
        Product gaseosa = new Product("Gaseosa", categories.get("GASEOSAS"), money("4000"));
        gaseosa.addVariant(new ProductVariant("Personal", money("4000"), 1));
        gaseosa.addVariant(new ProductVariant("Litro", money("6000"), 2));
        gaseosa.addVariant(new ProductVariant("1.5 L", money("8000"), 3));
        gaseosa.addVariant(new ProductVariant("2 L", money("9000"), 4));
        gaseosa.getAreas().add(areas.get("GASEOSAS"));
        gaseosa.setSortOrder(3);
        productRepository.save(gaseosa);

        // --- Jugos (sabor + tamano) ---
        Product jugo = new Product("Jugo", categories.get("JUGOS"), money("7000"));
        jugo.setRequiresFlavor(true);
        jugo.addVariant(new ProductVariant("En agua", money("7000"), 1));
        jugo.addVariant(new ProductVariant("En leche", money("8000"), 2));
        jugo.addFlavor(new Flavor("Naranja", money("0"), 1));
        jugo.addFlavor(new Flavor("Mora", money("0"), 2));
        jugo.addFlavor(new Flavor("Lulo", money("0"), 3));
        jugo.addFlavor(new Flavor("Mango", money("0"), 4));
        jugo.getAreas().add(areas.get("BEBIDAS"));
        jugo.setSortOrder(4);
        productRepository.save(jugo);

        // --- Limonadas ---
        Product limonada = new Product("Limonada", categories.get("LIMONADAS"), money("6000"));
        limonada.addFlavor(new Flavor("Natural", money("0"), 1));
        limonada.addFlavor(new Flavor("Cerezada", money("2000"), 2));
        limonada.addFlavor(new Flavor("Coco", money("2000"), 3));
        limonada.getAreas().add(areas.get("BEBIDAS"));
        limonada.setSortOrder(5);
        productRepository.save(limonada);

        // --- Milo ---
        Product milo = new Product("Milo", categories.get("MILO"), money("7000"));
        milo.addVariant(new ProductVariant("Caliente", money("7000"), 1));
        milo.addVariant(new ProductVariant("Frío", money("7000"), 2));
        milo.getAreas().add(areas.get("BEBIDAS"));
        milo.setSortOrder(6);
        productRepository.save(milo);

        // --- Lasagnas ---
        Product lasagna = new Product("Lasagna", categories.get("LASANAS"), money("22000"));
        lasagna.setRequiresFlavor(true);
        lasagna.addFlavor(new Flavor("Boloñesa", money("0"), 1));
        lasagna.addFlavor(new Flavor("Pollo", money("0"), 2));
        lasagna.addFlavor(new Flavor("Mixta", money("1000"), 3));
        lasagna.getAreas().add(areas.get("COCINA"));
        lasagna.setSortOrder(7);
        productRepository.save(lasagna);

        // --- Comidas ---
        Product hamburguesa = new Product("Hamburguesa", categories.get("COMIDAS"), money("18000"));
        hamburguesa.getAreas().add(areas.get("COMIDAS"));
        hamburguesa.setSortOrder(8);
        productRepository.save(hamburguesa);

        Product perro = new Product("Perro caliente", categories.get("COMIDAS"), money("12000"));
        perro.getAreas().add(areas.get("COMIDAS"));
        perro.setSortOrder(9);
        productRepository.save(perro);

        Product salchipapa = new Product("Salchipapa", categories.get("COMIDAS"), money("15000"));
        salchipapa.getAreas().add(areas.get("COMIDAS"));
        salchipapa.setSortOrder(10);
        productRepository.save(salchipapa);

        // --- Bebida simple ---
        Product agua = new Product("Agua", categories.get("BEBIDAS"), money("3000"));
        agua.getAreas().add(areas.get("BEBIDAS"));
        agua.setSortOrder(11);
        productRepository.save(agua);

        // --- Producto con DOS destinos (prueba de distribucion multiple sin duplicar) ---
        Product combo = new Product("Combo Pizza + Gaseosa", categories.get("PIZZAS"), money("40000"));
        combo.setRequiresFlavor(true);
        combo.setDescription("Producto de ejemplo con dos destinos: Pizzas y Gaseosas.");
        Set<PreparationArea> comboAreas = new LinkedHashSet<>();
        comboAreas.add(areas.get("PIZZAS"));
        comboAreas.add(areas.get("GASEOSAS"));
        combo.setAreas(comboAreas);
        combo.addFlavor(new Flavor("Hawaiana", money("0"), 1));
        combo.addFlavor(new Flavor("Pepperoni", money("2000"), 2));
        combo.setSortOrder(12);
        productRepository.save(combo);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
