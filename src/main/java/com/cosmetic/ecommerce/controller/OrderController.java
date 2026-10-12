package com.cosmetic.ecommerce.controller;

import com.cosmetic.ecommerce.entity.Order;
import com.cosmetic.ecommerce.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

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
    // GET /api/orders (සියලුම Orders ලබා ගැනීම)
    @GetMapping
    public ResponseEntity<List<Order>> getUserOrders() {
        String email = getLoggedInUserEmail();
        return ResponseEntity.ok(orderService.getUserOrders(email));
    }

    // GET /api/orders/{id} (එක් ඇණවුමක් පමණක් ලබා ගැනීම)
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable Long id) {
        String email = getLoggedInUserEmail();
        return ResponseEntity.ok(orderService.getOrderById(id, email));
    }
}