package com.restaurante.pedidos.web.api;

import com.restaurante.pedidos.dto.SystemDtos;
import com.restaurante.pedidos.service.PrintService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Impresion desde el servidor. El empleado solicita la impresion desde su dispositivo y el
 * servidor envia el trabajo a la impresora conectada a el. La configuracion de impresora se
 * gestiona en {@code /api/admin/print}.
 */
@RestController
@RequestMapping("/api/print")
public class PrintController {

    private final PrintService printService;

    public PrintController(PrintService printService) {
        this.printService = printService;
    }

    @PostMapping("/orders/{orderId}")
    public SystemDtos.PrintJobView print(@PathVariable Long orderId,
                                         @RequestBody SystemDtos.PrintRequest request,
                                         @RequestParam(value = "actor", required = false) String actor) {
        return printService.requestPrint(orderId, request, actor);
    }

    @GetMapping("/orders/{orderId}/jobs")
    public List<SystemDtos.PrintJobView> jobsForOrder(@PathVariable Long orderId) {
        return printService.jobsForOrder(orderId);
    }

    @GetMapping("/jobs/recent")
    public List<SystemDtos.PrintJobView> recent() {
        return printService.recentJobs();
    }

    @GetMapping("/jobs/{jobId}")
    public SystemDtos.PrintJobView job(@PathVariable Long jobId) {
        return printService.getJob(jobId);
    }

    @PostMapping("/jobs/{jobId}/retry")
    public SystemDtos.PrintJobView retry(@PathVariable Long jobId,
                                         @RequestParam(value = "actor", required = false) String actor) {
        return printService.retryPrint(jobId, actor);
    }

    @GetMapping("/config")
    public SystemDtos.PrintConfigView config() {
        return printService.config();
    }
}
