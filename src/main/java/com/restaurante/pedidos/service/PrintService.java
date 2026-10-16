package com.restaurante.pedidos.service;

import com.restaurante.pedidos.config.PedidosProperties;
import com.restaurante.pedidos.domain.AdminEventType;
import com.restaurante.pedidos.domain.Order;
import com.restaurante.pedidos.domain.OrderEventType;
import com.restaurante.pedidos.domain.PreparationArea;
import com.restaurante.pedidos.domain.PrintJob;
import com.restaurante.pedidos.domain.PrintJobStatus;
import com.restaurante.pedidos.dto.SystemDtos;
import com.restaurante.pedidos.exception.BusinessException;
import com.restaurante.pedidos.exception.NotFoundException;
import com.restaurante.pedidos.print.TicketRenderer;
import com.restaurante.pedidos.repository.OrderEventRepository;
import com.restaurante.pedidos.repository.OrderRepository;
import com.restaurante.pedidos.repository.PreparationAreaRepository;
import com.restaurante.pedidos.repository.PrintJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Impresion desde el computador servidor.
 *
 * <p>El empleado solicita la impresion desde su dispositivo; la peticion llega al
 * servidor y este escribe en la impresora conectada a el (javax.print). No se depende de
 * la ventana de impresion del navegador del cliente.</p>
 *
 * <p>Modos:</p>
 * <ul>
 *   <li>{@code SIMULATION}: escribe la comanda en {@code data/print-simulation/} y marca el
 *       trabajo como ACEPTADO indicando claramente que NO se imprimio fisicamente.</li>
 *   <li>{@code REAL}: envia a la impresora del servidor. ACEPTADO significa aceptado por la
 *       cola del sistema operativo; NO confirma que el papel haya salido.</li>
 * </ul>
 *
 * <p>Cada intento tiene su propio registro con id de deduplicacion, evitando dobles
 * clics. Los reintentos crean un nuevo intento sin borrar el anterior.</p>
 */
@Service
public class PrintService {

    private static final Logger log = LoggerFactory.getLogger(PrintService.class);
    private static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final PrintJobRepository printJobRepository;
    private final OrderRepository orderRepository;
    private final PreparationAreaRepository areaRepository;
    private final OrderEventRepository orderEventRepository;
    private final SettingsService settingsService;
    private final PedidosProperties properties;

    public PrintService(PrintJobRepository printJobRepository,
                        OrderRepository orderRepository,
                        PreparationAreaRepository areaRepository,
                        OrderEventRepository orderEventRepository,
                        SettingsService settingsService,
                        PedidosProperties properties) {
        this.printJobRepository = printJobRepository;
        this.orderRepository = orderRepository;
        this.areaRepository = areaRepository;
        this.orderEventRepository = orderEventRepository;
        this.settingsService = settingsService;
        this.properties = properties;
    }

    // =====================================================================
    //  Solicitud de impresion
    // =====================================================================

    @Transactional
    public SystemDtos.PrintJobView requestPrint(Long orderId, SystemDtos.PrintRequest request, String actor) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Pedido no encontrado: id " + orderId));

        PreparationArea area = null;
        String target = request.target() == null ? "FULL" : request.target().trim().toUpperCase(Locale.ROOT);
        if ("AREA".equals(target)) {
            if (request.areaId() == null) {
                throw new BusinessException("Debe indicar el área a imprimir.");
            }
            area = areaRepository.findById(request.areaId())
                    .orElseThrow(() -> new BusinessException("Área no encontrada."));
        } else if (!"FULL".equals(target)) {
            throw new BusinessException("Destino de impresión no válido: " + request.target());
        }

        boolean reprint = Boolean.TRUE.equals(request.reprint());

        // Deduplicacion: evita que un doble clic cree trabajos identicos.
        String dedupeKey = buildDedupeKey(order, area, reprint);
        if (!reprint) {
            LocalDateTime windowStart = LocalDateTime.now().minusSeconds(properties.getPrint().getDedupeWindowSeconds());
            var recent = printJobRepository.findFirstByDedupeKeyAndCreatedAtAfter(dedupeKey, windowStart);
            if (recent.isPresent()) {
                return toView(recent.get());
            }
        }

        if (reprint) {
            boolean alreadyAccepted = printJobRepository.existsByOrderIdAndSourceVersionAndStatus(
                    orderId, order.getContentVersion(), PrintJobStatus.ACEPTADO);
            if (!alreadyAccepted) {
                // No es realmente una reimpresion; se acepta igualmente.
                log.debug("Reimpresion solicitada sin trabajo aceptado previo para {}", order.getCode());
            }
        }

        PrintJob job = new PrintJob(order, area, order.getContentVersion());
        job.setReprint(reprint);
        job.setDedupeKey(dedupeKey);
        job.setAttempt(nextAttempt(orderId, area));
        String content = TicketRenderer.render(order, area, reprint,
                new TicketRenderer.Context(settingsService.getBusinessName(),
                        settingsService.getCurrencySymbol(), settingsService.getPaperWidth()));
        job.setContent(content);
        job.setStatus(PrintJobStatus.ENVIANDO);
        job.setSentAt(LocalDateTime.now());
        job.setPrinterName(settingsService.getPrinterName());

        boolean simulation = isSimulationMode();
        job.setSimulated(simulation);
        performSend(job, simulation);

        job.setCompletedAt(LocalDateTime.now());
        PrintJob saved = printJobRepository.save(job);

        // Si la impresion completa fue aceptada, se marca la version impresa.
        if (saved.getStatus() == PrintJobStatus.ACEPTADO && area == null) {
            order.setLastPrintedVersion(order.getContentVersion());
            order.setNeedsReprint(false);
            orderRepository.save(order);
        }
        orderEventRepository.save(new com.restaurante.pedidos.domain.OrderEvent(order,
                reprint ? OrderEventType.IMPRESION_REINTENTO : OrderEventType.IMPRESION_SOLICITADA,
                (area == null ? "Pedido completo" : "Área: " + area.getName())
                        + " -> " + saved.getStatus().getLabel()
                        + (simulation ? " (simulado)" : ""), actor));
        return toView(saved);
    }

    /** Reintenta un trabajo fallido o existente creando un nuevo intento. */
    @Transactional
    public SystemDtos.PrintJobView retryPrint(Long jobId, String actor) {
        PrintJob original = printJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Trabajo de impresión no encontrado: id " + jobId));
        Order order = original.getOrder();
        PreparationArea area = original.getArea();

        PrintJob job = new PrintJob(order, area, order.getContentVersion());
        job.setReprint(true);
        job.setDedupeKey(buildDedupeKey(order, area, true) + "-r" + System.nanoTime());
        job.setAttempt(original.getAttempt() + 1);
        String content = TicketRenderer.render(order, area, true,
                new TicketRenderer.Context(settingsService.getBusinessName(),
                        settingsService.getCurrencySymbol(), settingsService.getPaperWidth()));
        job.setContent(content);
        job.setStatus(PrintJobStatus.ENVIANDO);
        job.setSentAt(LocalDateTime.now());
        job.setPrinterName(settingsService.getPrinterName());
        boolean simulation = isSimulationMode();
        job.setSimulated(simulation);
        performSend(job, simulation);
        job.setCompletedAt(LocalDateTime.now());
        PrintJob saved = printJobRepository.save(job);

        orderEventRepository.save(new com.restaurante.pedidos.domain.OrderEvent(order,
                OrderEventType.IMPRESION_REINTENTO,
                "Reintento de impresión (intento " + saved.getAttempt() + ") -> " + saved.getStatus().getLabel(),
                actor));
        return toView(saved);
    }

    // =====================================================================
    //  Envio efectivo
    // =====================================================================

    private void performSend(PrintJob job, boolean simulation) {
        if (simulation) {
            sendSimulation(job);
        } else {
            sendReal(job);
        }
    }

    private void sendSimulation(PrintJob job) {
        try {
            Path dir = Path.of(properties.getPrint().getSimulationDir());
            Files.createDirectories(dir);
            String fileName = job.getOrder().getCode()
                    + (job.getArea() != null ? "-" + job.getArea().getCode() : "-FULL")
                    + "-" + LocalDateTime.now().format(FILE_FMT)
                    + "-attempt" + job.getAttempt() + ".txt";
            Path file = dir.resolve(fileName);
            Files.writeString(file, job.getContent(), StandardCharsets.UTF_8);
            job.setStatus(PrintJobStatus.ACEPTADO);
            job.setSimulated(true);
            // Honestidad: la simulacion NO imprime fisicamente.
            job.setPhysicalConfirmed(false);
            job.setMessage("SIMULACIÓN: comanda guardada en " + file.toAbsolutePath()
                    + ". No se imprimió en papel.");
        } catch (Exception e) {
            job.setStatus(PrintJobStatus.ERROR);
            job.setMessage("Error al escribir la simulación: " + e.getMessage());
        }
    }

    private void sendReal(PrintJob job) {
        String printerName = job.getPrinterName();
        try {
            javax.print.PrintService target = findPrinter(printerName);
            if (target == null) {
                job.setStatus(PrintJobStatus.ERROR);
                job.setMessage(printerName == null || printerName.isBlank()
                        ? "No hay impresora configurada. Configúrela en Administración > Impresión."
                        : "No se encontró la impresora '" + printerName + "' en el servidor.");
                return;
            }
            byte[] bytes = job.getContent().getBytes(StandardCharsets.UTF_8);
            Doc doc = new SimpleDoc(new ByteArrayInputStream(bytes), DocFlavor.INPUT_STREAM.AUTOSENSE, null);
            DocPrintJob printJob = target.createPrintJob();
            printJob.print(doc, new HashPrintRequestAttributeSet());
            // La cola acepto el trabajo; NO podemos confirmar que el papel salio.
            job.setStatus(PrintJobStatus.ACEPTADO);
            job.setPhysicalConfirmed(false);
            job.setMessage("Aceptado por la cola de impresión de '" + target.getName()
                    + "'. No es posible confirmar fisicamente el papel desde el servidor.");
        } catch (Exception e) {
            // Si no podemos saber si fallo o no, el estado es DESCONOCIDO.
            job.setStatus(PrintJobStatus.DESCONOCIDO);
            job.setMessage("No fue posible confirmar el resultado: " + e.getMessage()
                    + ". Verifique la impresora y reintente si es necesario.");
            log.warn("Fallo al enviar trabajo de impresion: {}", e.getMessage());
        }
    }

    private javax.print.PrintService findPrinter(String printerName) {
        javax.print.PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        if (services == null || services.length == 0) {
            return null;
        }
        if (printerName == null || printerName.isBlank()) {
            return PrintServiceLookup.lookupDefaultPrintService();
        }
        for (javax.print.PrintService s : services) {
            if (s.getName().equalsIgnoreCase(printerName)) {
                return s;
            }
        }
        return null;
    }

    // =====================================================================
    //  Consultas y configuracion
    // =====================================================================

    @Transactional(readOnly = true)
    public List<SystemDtos.PrintJobView> jobsForOrder(Long orderId) {
        List<SystemDtos.PrintJobView> out = new ArrayList<>();
        for (PrintJob job : printJobRepository.findByOrderIdOrderByCreatedAtAsc(orderId)) {
            out.add(toView(job));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<SystemDtos.PrintJobView> recentJobs() {
        List<SystemDtos.PrintJobView> out = new ArrayList<>();
        for (PrintJob job : printJobRepository.findTop100ByOrderByCreatedAtDesc()) {
            out.add(toView(job));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public SystemDtos.PrintJobView getJob(Long jobId) {
        return toView(printJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Trabajo no encontrado: id " + jobId)));
    }

    @Transactional(readOnly = true)
    public SystemDtos.PrintConfigView config() {
        List<String> printers = new ArrayList<>();
        try {
            javax.print.PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
            if (services != null) {
                for (javax.print.PrintService s : services) {
                    printers.add(s.getName());
                }
            }
        } catch (Exception e) {
            log.warn("No se pudo listar impresoras: {}", e.getMessage());
        }
        boolean real = !isSimulationMode();
        return new SystemDtos.PrintConfigView(
                properties.getPrint().getMode().toUpperCase(Locale.ROOT),
                settingsService.getPrinterName(),
                settingsService.getPaperWidth(),
                real,
                printers
        );
    }

    @Transactional
    public SystemDtos.PrintConfigView updateConfig(SystemDtos.PrintConfigRequest request) {
        if (request.printerName() != null) {
            settingsService.setPrinterName(request.printerName().trim());
        }
        if (request.paperWidth() != null) {
            settingsService.setPaperWidth(request.paperWidth());
        }
        return config();
    }

    public boolean isSimulationMode() {
        return !"REAL".equalsIgnoreCase(properties.getPrint().getMode());
    }

    private int nextAttempt(Long orderId, PreparationArea area) {
        List<PrintJob> jobs = printJobRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
        int max = 0;
        for (PrintJob j : jobs) {
            boolean sameArea = (area == null && j.getArea() == null)
                    || (area != null && j.getArea() != null && j.getArea().getId().equals(area.getId()));
            if (sameArea) {
                max = Math.max(max, j.getAttempt());
            }
        }
        return max + 1;
    }

    private String buildDedupeKey(Order order, PreparationArea area, boolean reprint) {
        return order.getId() + ":" + (area == null ? "FULL" : "A" + area.getId())
                + ":v" + order.getContentVersion() + (reprint ? ":R" : "");
    }

    private SystemDtos.PrintJobView toView(PrintJob job) {
        return new SystemDtos.PrintJobView(
                job.getId(),
                job.getOrder().getId(),
                job.getOrder().getCode(),
                job.getArea() != null ? job.getArea().getId() : null,
                job.getArea() != null ? job.getArea().getName() : null,
                job.getStatus().name(),
                job.getStatus().getLabel(),
                job.getStatus().getColor(),
                job.getAttempt(),
                job.getSourceVersion(),
                job.isReprint(),
                job.isSimulated(),
                job.isPhysicalConfirmed(),
                job.getPrinterName(),
                job.getMessage(),
                job.getCreatedAt() != null ? job.getCreatedAt().toString() : null,
                job.getCompletedAt() != null ? job.getCompletedAt().toString() : null,
                job.getContent()
        );
    }

    /** Reafirmacion explicita del modo para mostrar en el panel. */
    public String modeDescription() {
        if (isSimulationMode()) {
            return "MODO SIMULACIÓN: las comandas se guardan en data/print-simulation y NO se imprimen en papel.";
        }
        return "MODO REAL: se envia a la impresora del servidor. ACEPTADO = aceptado por la cola (no confirma papel).";
    }
}
