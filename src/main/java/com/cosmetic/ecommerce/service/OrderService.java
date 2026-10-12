package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.entity.*;
import com.cosmetic.ecommerce.repository.OrderRepository;
import com.cosmetic.ecommerce.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final ProductRepository productRepository;

    public OrderService(OrderRepository orderRepository, CartService cartService, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.cartService = cartService;
        this.productRepository = productRepository;
    }

    @Transactional
    public Order createOrder(String email, String shippingAddress) {
        if (shippingAddress == null || shippingAddress.trim().isEmpty()) {
            throw new RuntimeException("Shipping address is required");
        }

        Cart cart = cartService.getCart(email);
        if (cart.getItems().isEmpty()) {
            throw new RuntimeException("Cannot place order. Cart is empty.");
        }

        Order order = new Order();
        order.setUser(cart.getUser());
        order.setShippingAddress(shippingAddress);
        order.setTotalAmount(cart.getTotalPrice());
        order.setStatus("PENDING");

        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();

            if (product.getStockQuantity() < cartItem.getQuantity()) {
                throw new RuntimeException("Not enough stock for product: " + product.getName());
            }

            product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());
            productRepository.save(product);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setPrice(cartItem.getPrice());

            order.getOrderItems().add(orderItem);
        }

        Order savedOrder = orderRepository.save(order);
        cartService.clearCart(email); // Order එක සාර්ථක නිසා Cart එක හිස් කරනවා

        return savedOrder;
    }
    // 1. User ගේ සියලුම Orders බැලීම
    public List<Order> getUserOrders(String email) {
        return orderRepository.findByUserEmail(email);
    }

    // 2. එක Order එකක් පමණක් තෝරා බැලීම
    public Order getOrderById(Long orderId, String email) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // වෙනත් අයගේ Orders බලන්න බැරි වෙන්න Security Check එකක්
        if (!order.getUser().getEmail().equals(email)) {
            throw new RuntimeException("Unauthorized to view this order");
        }
        return order;
    }
}