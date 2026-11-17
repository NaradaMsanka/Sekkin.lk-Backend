package com.cosmetic.ecommerce.controller;

import com.cosmetic.ecommerce.dto.*;
import com.cosmetic.ecommerce.entity.Order;
import com.cosmetic.ecommerce.entity.OrderItem;
import com.cosmetic.ecommerce.service.OrderService;
import com.cosmetic.ecommerce.service.OrderPlacementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;
    private final OrderPlacementService placement;
    public OrderController(OrderService service, OrderPlacementService placement) { orderService = service; this.placement = placement; }

    private String loggedInEmail() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName()) ? auth.getName() : null;
    }

    @PostMapping("/direct")
    public ResponseEntity<DirectOrderResponseDTO> createDirectOrder(@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody DirectOrderRequestDTO request) {
        Order order = placement.placeDirect(key, request, loggedInEmail());
        OrderItem first = order.getOrderItems().getFirst();
        BigDecimal subtotal = BigDecimal.valueOf(first.getPrice()).multiply(BigDecimal.valueOf(first.getQuantity())).setScale(2, RoundingMode.HALF_UP);
        return ResponseEntity.ok(DirectOrderResponseDTO.builder()
                .orderId(order.getId()).customerName(order.getCustomerName()).customerPhone(order.getCustomerPhone())
                .customerEmail(order.getCustomerEmail()).shippingAddress(order.getShippingAddress()).postalCode(order.getPostalCode())
                .paymentMethod(order.getPaymentMethod()).paymentStatus(order.getPaymentStatus()).deliveryCharge(order.getDeliveryCharge())
                .subtotal(subtotal.doubleValue()).totalAmount(order.getTotalAmount()).discountAmount(order.getDiscountAmount())
                .couponDiscount(subtotal.add(BigDecimal.valueOf(order.getDeliveryCharge())).subtract(BigDecimal.valueOf(order.getTotalAmount()))
                        .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP).doubleValue())
                .status(order.getStatus()).orderDate(order.getOrderDate())
                .productName(first.getProduct().getName()).quantity(first.getQuantity()).unitPrice(first.getPrice())
                .message("Order request received. Acceptance requires business confirmation. This receipt is not proof of payment.").build());
    }

    @PostMapping
    public ResponseEntity<OrderReceiptDTO> createOrder(@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CartOrderRequestDTO request) {
        return ResponseEntity.ok(OrderReceiptDTO.from(placement.placeCart(key, request, loggedInEmail())));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponseDTO>> getUserOrders() {
        return ResponseEntity.ok(orderService.getUserOrders(loggedInEmail()).stream().map(order -> {
            OrderResponseDTO dto = new OrderResponseDTO(order.getId(), order.getShippingAddress(), order.getTotalAmount(), order.getStatus(), order.getOrderDate());
            dto.setDeliveryCharge(order.getDeliveryCharge()); return dto;
        }).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponseDTO> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(summary(orderService.getOrderById(id, loggedInEmail())));
    }

    @RequestMapping(value = "/{id}/status", method = {RequestMethod.PATCH, RequestMethod.PUT})
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<OrderResponseDTO> updateOrderStatus(@PathVariable Long id, @RequestParam(required = false) String status,
            @RequestBody(required = false) Map<String, String> body) {
        if (status == null && body != null) status = body.get("status");
        return ResponseEntity.ok(summary(orderService.updateOrderStatus(id, status)));
    }

    private OrderResponseDTO summary(Order order) {
        OrderResponseDTO dto = new OrderResponseDTO(order.getId(), order.getShippingAddress(), order.getTotalAmount(), order.getStatus(), order.getOrderDate());
        dto.setDeliveryCharge(order.getDeliveryCharge());
        return dto;
    }
}
