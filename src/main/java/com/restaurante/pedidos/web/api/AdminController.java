package com.restaurante.pedidos.web.api;

import com.restaurante.pedidos.dto.SystemDtos;
import com.restaurante.pedidos.service.AdminAuthService;
import com.restaurante.pedidos.service.AdminService;
import com.restaurante.pedidos.service.BackupService;
import com.restaurante.pedidos.service.LockService;
import com.restaurante.pedidos.service.PrintService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Administracion protegida: sesion, credenciales, bloqueo temporal, correo, eventos,
 * configuracion del negocio, impresora y copias de seguridad.
 *
 * <p>Salvo login/logout/session, todas las rutas exigen sesion administrativa (ver
 * {@code AdminInterceptor}).</p>
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminAuthService adminAuthService;
    private final AdminService adminService;
    private final LockService lockService;
    private final BackupService backupService;
    private final PrintService printService;

    public AdminController(AdminAuthService adminAuthService,
                           AdminService adminService,
                           LockService lockService,
                           BackupService backupService,
                           PrintService printService) {
        this.adminAuthService = adminAuthService;
        this.adminService = adminService;
        this.lockService = lockService;
        this.backupService = backupService;
        this.printService = printService;
    }

    // --- Sesion ------------------------------------------------------------

    @PostMapping("/login")
    public SystemDtos.AdminSessionView login(@RequestBody SystemDtos.AdminLoginRequest request,
                                             HttpServletRequest http) {
        String key = clientKey(http);
        adminAuthService.login(request == null ? null : request.password(), http.getSession(true), key);
        return session(http);
    }

    @PostMapping("/logout")
    public SystemDtos.AdminSessionView logout(HttpServletRequest http) {
        adminAuthService.logout(http.getSession(true), clientKey(http));
        return session(http);
    }

    @GetMapping("/session")
    public SystemDtos.AdminSessionView session(HttpServletRequest http) {
        HttpSession session = http.getSession(true);
        return new SystemDtos.AdminSessionView(
                adminAuthService.isAuthenticated(session),
                null,
                lockService.status().mustChangePassword(),
                lockService.isLocked());
    }

    // --- Bloqueo temporal --------------------------------------------------

    @GetMapping("/lock/status")
    public SystemDtos.LockStatusView lockStatus() {
        return lockService.status();
    }

    @PostMapping("/lock/renew")
    public SystemDtos.LockStatusView renew(@RequestBody SystemDtos.RenewRequest request,
                                           HttpServletRequest http) {
        return lockService.validateAndRenew(request == null ? null : request.password(), clientKey(http));
    }

    @PutMapping("/lock/settings")
    public SystemDtos.LockStatusView lockSettings(@RequestBody SystemDtos.LockSettingsRequest request,
                                                  HttpServletRequest http) {
        return lockService.updateSettings(request, clientKey(http));
    }

    // --- Contrasenas -------------------------------------------------------

    @PostMapping("/password")
    public SystemDtos.LockStatusView changePassword(@RequestBody SystemDtos.ChangePasswordRequest request,
                                                    HttpServletRequest http) {
        lockService.changeMainPassword(request.currentPassword(), request.newPassword(),
                request.confirmPassword(), clientKey(http));
        return lockService.status();
    }

    @PostMapping("/temp-password")
    public SystemDtos.LockStatusView setTempPassword(@RequestBody SystemDtos.TempPasswordRequest request,
                                                     HttpServletRequest http) {
        return lockService.setTempPassword(request.password(), request.validHours(), clientKey(http));
    }

    @DeleteMapping("/temp-password")
    public SystemDtos.LockStatusView clearTempPassword(HttpServletRequest http) {
        return lockService.clearTempPassword(clientKey(http));
    }

    // --- Configuracion general --------------------------------------------

    @GetMapping("/settings")
    public SystemDtos.SettingView settings() {
        return adminService.settings();
    }

    @PutMapping("/settings")
    public SystemDtos.SettingView updateSettings(@RequestBody SystemDtos.SettingRequest request,
                                                 HttpServletRequest http) {
        return adminService.updateSettings(request, clientKey(http));
    }

    // --- Impresora ---------------------------------------------------------

    @GetMapping("/print/config")
    public SystemDtos.PrintConfigView printConfig() {
        return printService.config();
    }

    @PutMapping("/print/config")
    public SystemDtos.PrintConfigView updatePrintConfig(@RequestBody SystemDtos.PrintConfigRequest request) {
        return printService.updateConfig(request);
    }

    // --- Copias de seguridad ----------------------------------------------

    @GetMapping("/backup")
    public List<SystemDtos.BackupView> backups() {
        return backupService.list();
    }

    @PostMapping("/backup")
    public SystemDtos.BackupView createBackup(HttpServletRequest http) {
        return backupService.createBackup(clientKey(http));
    }

    // --- Eventos -----------------------------------------------------------

    @GetMapping("/events")
    public List<SystemDtos.AdminEventView> events() {
        return adminService.events();
    }

    private String clientKey(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
