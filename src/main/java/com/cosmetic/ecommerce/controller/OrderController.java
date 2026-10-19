package com.cosmetic.ecommerce.controller;

import com.cosmetic.ecommerce.dto.OrderResponseDTO;
import com.cosmetic.ecommerce.entity.Order;
import com.cosmetic.ecommerce.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    // POST /api/orders (Day 26: shippingAddress සහ optional couponCode ලබා ගැනීම)
    @PostMapping
    public ResponseEntity<Order> createOrder(
            @RequestParam(required = false) String shippingAddress,
            @RequestParam(required = false) String couponCode,
            @RequestBody(required = false) Map<String, String> body) {

        String email = getLoggedInUserEmail();

        // Query Param එකෙන් හෝ JSON Body එකෙන් එවන ඕනෑම ආකාරයක් ලබා ගැනීම
        if (shippingAddress == null && body != null) {
            shippingAddress = body.get("shippingAddress");
        }
        if (couponCode == null && body != null) {
            couponCode = body.get("couponCode");
        }

        Order order = orderService.createOrder(email, shippingAddress, couponCode);
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

    // PATCH හෝ PUT /api/orders/{id}/status (Admin Dashboard සහ Postman දෙකටම වැඩ කරන ලෙස)
    @RequestMapping(value = "/{id}/status", method = {RequestMethod.PATCH, RequestMethod.PUT})
    @PreAuthorize("hasAuthority('ADMIN')") // hasRole('ADMIN') requires "ROLE_ADMIN" prefix; JwtFilter stores plain "ADMIN"
    public ResponseEntity<Order> updateOrderStatus(
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @RequestBody(required = false) Map<String, String> body) {

        if (status == null && body != null) {
            status = body.get("status");
        }

        Order updatedOrder = orderService.updateOrderStatus(id, status);
        return ResponseEntity.ok(updatedOrder);
    }
}