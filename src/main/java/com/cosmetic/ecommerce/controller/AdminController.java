package com.cosmetic.ecommerce.controller;

import com.cosmetic.ecommerce.dto.AdminDashboardDTO;
import com.cosmetic.ecommerce.dto.UserSummaryDTO;
import com.cosmetic.ecommerce.service.AdminService;
import com.cosmetic.ecommerce.service.OrderService;
import com.cosmetic.ecommerce.service.AccountSecurityService;
import com.cosmetic.ecommerce.dto.AdminOrderDTO;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('ADMIN')") // Bug Fix: AdminController had no security protection!
public class AdminController {

    private final AdminService adminService;
    private final OrderService orders;
    private final AccountSecurityService accounts;

    public AdminController(AdminService adminService, OrderService orders, AccountSecurityService accounts) {
        this.adminService = adminService;
        this.orders = orders; this.accounts = accounts;
    }

    // GET /api/admin/dashboard
    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardDTO> getDashboard() {
        return ResponseEntity.ok(adminService.getDashboardSummary());
    }

    // GET /api/admin/users
    @GetMapping("/users")
    public ResponseEntity<List<UserSummaryDTO>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @GetMapping("/orders")
    public ResponseEntity<List<AdminOrderDTO>> getOrders() { return ResponseEntity.ok(adminService.getAllOrders()); }

    public record Acceptance(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 160) String reference) {}
    public record Collection(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0.01")
            @jakarta.validation.constraints.Digits(integer = 15, fraction = 2)
            @jakarta.validation.constraints.DecimalMax("999999999999999.99") java.math.BigDecimal amount,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 160) String reference) {}

    @PostMapping("/orders/{id}/accept")
    public ResponseEntity<AdminOrderDTO> accept(@PathVariable Long id, @jakarta.validation.Valid @RequestBody Acceptance request,
            java.security.Principal operator) {
        orders.acceptOrder(id, request.reference(), operator.getName());
        return ResponseEntity.ok(adminService.getOrder(id));
    }

    @PostMapping("/orders/{id}/cod-collection")
    public ResponseEntity<AdminOrderDTO> collect(@PathVariable Long id, @jakarta.validation.Valid @RequestBody Collection request,
            java.security.Principal operator) {
        orders.recordCodCollection(id, request.amount(), request.reference(), operator.getName());
        return ResponseEntity.ok(adminService.getOrder(id));
    }

    @PostMapping("/users/{id}/revoke-sessions")
    public ResponseEntity<Void> revoke(@PathVariable Long id) {
        try { accounts.revokeSessionsForUser(id); }
        catch (IllegalArgumentException denied) { return ResponseEntity.status(409).build(); }
        return ResponseEntity.noContent().build();
    }

    public record AccountEnabled(@jakarta.validation.constraints.NotNull Boolean enabled) {}
    @PatchMapping("/users/{id}/enabled")
    public ResponseEntity<Void> enabled(@PathVariable Long id, @jakarta.validation.Valid @RequestBody AccountEnabled request,
            java.security.Principal operator) {
        try { accounts.setAccountEnabled(id, request.enabled(), operator.getName()); }
        catch (IllegalArgumentException denied) { return ResponseEntity.status(409).build(); }
        return ResponseEntity.noContent().build();
    }
}
