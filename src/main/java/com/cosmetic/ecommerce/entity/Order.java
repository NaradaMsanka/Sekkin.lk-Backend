package com.cosmetic.ecommerce.entity;

import com.fasterxml.jackson.annotation.JsonIgnore; // අලුතින් එකතු කළ import එක
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders") // SQL වල order කියන්නේ reserve වචනයක් නිසා orders ලෙස භාවිතා කරයි
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ඇණවුම දැමූ පුද්ගලයා
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore // මෙන්න මෙය එකතු කළා (Jackson මඟින් Proxy error එක මඟහරී)
    private User user;

    // භාණ්ඩ යැවිය යුතු ලිපිනය (Address එක මෙතන තියෙනවා)
    private String shippingAddress;

    // ඇණවුම් කළ මුළු මුදල
    private Double totalAmount;

    // ඇණවුමේ තත්ත්වය
    private String status = "PENDING";

    // ඇණවුම දැමූ දිනය සහ වේලාව
    private LocalDateTime orderDate = LocalDateTime.now();

    // ඇණවුමට අදාළ භාණ්ඩ ලැයිස්තුව
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }

    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDateTime orderDate) { this.orderDate = orderDate; }

    public List<OrderItem> getOrderItems() { return orderItems; }
    public void setOrderItems(List<OrderItem> orderItems) { this.orderItems = orderItems; }
}