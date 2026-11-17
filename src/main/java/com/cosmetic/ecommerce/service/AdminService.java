package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.dto.*;
import com.cosmetic.ecommerce.entity.Order;
import com.cosmetic.ecommerce.entity.User;
import com.cosmetic.ecommerce.repository.OrderRepository;
import com.cosmetic.ecommerce.repository.ProductRepository;
import com.cosmetic.ecommerce.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class AdminService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public AdminService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    // 1. Dashboard Summary ලබා ගැනීම
    public AdminDashboardDTO getDashboardSummary() {
        AdminDashboardDTO dashboard = new AdminDashboardDTO();

        List<Order> allOrders = orderRepository.findAll();

        // මුළු ආදායම ගණනය කිරීම (CANCELLED නොවන ඇණවුම් වල එකතුව)
        double totalRevenue = allOrders.stream()
                .filter(o -> o.getTotalAmount() != null && "COD".equals(o.getPaymentMethod())
                        && "PAID".equals(o.getPaymentStatus()) && o.getCodCollectedAt() != null)
                .mapToDouble(Order::getTotalAmount)
                .sum();

        dashboard.setTotalRevenue(totalRevenue);
        dashboard.setTotalOrders((long) allOrders.size());
        dashboard.setTotalProducts(productRepository.count());
        dashboard.setTotalUsers(userRepository.count());

        // තොග ප්‍රමාණය 10 හෝ ඊට අඩු භාණ්ඩ (Low-stock alerts)
        List<LowStockProductDTO> lowStock = productRepository.findByStockQuantityLessThanEqual(10)
                .stream()
                .map(p -> new LowStockProductDTO(
                        p.getId(),
                        p.getName(),
                        p.getBrand(),
                        p.getStockQuantity(),
                        p.getPrice()
                ))
                .collect(Collectors.toList());
        dashboard.setLowStockProducts(lowStock);

        // අලුත්ම ඇණවුම් 5 (Recent orders)
        List<RecentOrderDTO> recentOrders = orderRepository.findTop5ByOrderByOrderDateDesc()
                .stream()
                .map(o -> new RecentOrderDTO(
                        o.getId(),
                        o.getUser() != null ? o.getUser().getEmail() : "N/A",
                        o.getTotalAmount(),
                        o.getStatus(),
                        o.getOrderDate()
                ))
                .collect(Collectors.toList());
        dashboard.setRecentOrders(recentOrders);

        return dashboard;
    }

    // 2. සියලුම Users ලාගේ විස්තර ලබා ගැනීම
    public List<UserSummaryDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToUserDTO)
                .collect(Collectors.toList());
    }

    public List<AdminOrderDTO> getAllOrders() {
        return orderRepository.findAll().stream().map(AdminOrderDTO::from).toList();
    }

    public AdminOrderDTO getOrder(Long id) {
        return AdminOrderDTO.from(orderRepository.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Order not found")));
    }

    private UserSummaryDTO mapToUserDTO(User user) {
        UserSummaryDTO dto = new UserSummaryDTO();
        dto.setId(user.getId());
        dto.setName(user.getName()); // ඔබේ User entity එකේ username හෝ fullName නම් ඒ අනුව වෙනස් කරන්න
        dto.setEmail(user.getEmail());
        dto.setRole(java.util.Objects.toString(user.getRole(), "UNSUPPORTED")); // Role එක String එකක් නම් .toString() අවශ්‍ය නැත
        return dto;
    }
}
