package com.cosmetic.ecommerce.controller;

import com.cosmetic.ecommerce.dto.OrderResponseDTO; // DTO එක ඉම්පෝට් කිරීම
import com.cosmetic.ecommerce.entity.Order;
import com.cosmetic.ecommerce.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    private String getLoggedInUserEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestParam String shippingAddress) {
        String email = getLoggedInUserEmail();
        Order order = orderService.createOrder(email, shippingAddress);
        return ResponseEntity.ok(order);
    }

    // GET /api/orders (සියලුම Orders DTO මඟින් ලබා ගැනීම - Serialization දෝෂය මඟහරී)
    @GetMapping
    public ResponseEntity<List<OrderResponseDTO>> getUserOrders() {
        String email = getLoggedInUserEmail();
        List<Order> orders = orderService.getUserOrders(email);

        // Entity ලැයිස්තුව DTO ලැයිස්තුවක් බවට හැරවීම
        List<OrderResponseDTO> orderDTOs = orders.stream().map(order ->
                new OrderResponseDTO(
                        order.getId(),
                        order.getShippingAddress(),
                        order.getTotalAmount(),
                        order.getStatus(),
                        order.getOrderDate()
                )
        ).collect(Collectors.toList());

        return ResponseEntity.ok(orderDTOs);
    }

    // GET /api/orders/{id} (එක් ඇණවුමක් පමණක් ලබා ගැනීම)
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable Long id) {
        String email = getLoggedInUserEmail();
        return ResponseEntity.ok(orderService.getOrderById(id, email));
    }

    // PATCH /api/orders/{id}/status (Admin පමණයි)
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Order> updateOrderStatus(
            @PathVariable Long id,
            @RequestParam String status) {

        Order updatedOrder = orderService.updateOrderStatus(id, status);
        return ResponseEntity.ok(updatedOrder);
    }
}