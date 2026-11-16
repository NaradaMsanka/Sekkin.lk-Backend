package com.cosmetic.ecommerce.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    @JoinColumn(name = "user_id", nullable = true) // Allows guest direct checkout as well as logged in user
    @JsonIgnore
    private User user;

    // Customer details for checkout
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private String postalCode;

    // භාණ්ඩ යැවිය යුතු ලිපිනය
    private String shippingAddress;

    // Payment details: "CARD", "KOKO", "COD"
    private String paymentMethod = "COD";
    private Double deliveryCharge = 0.0;
    private String paymentStatus = "PENDING"; // "PAID", "KOKO_APPROVED", "PENDING_COD"

    // ඇණවුම් කළ මුළු මුදල (වට්ටම් අඩු කිරීමෙන් පසු අවසාන මුදල)
    private Double totalAmount;

    // --- Day 26: Simple Coupon & Discount Fields ---
    // පාරිභෝගිකයා භාවිතා කළ Coupon කේතය (උදා: GLOW10, SAVE500)
    private String couponCode;

    // Coupon හෝ වට්ටම් මඟින් අඩු වූ මුදල
    private Double discountAmount = 0.0;

    // ඇණවුමේ තත්ත්වය
    private String status = "PENDING";

    // ඇණවුම දැමූ දිනය සහ වේලාව
    private LocalDateTime orderDate = LocalDateTime.now();

    // ඇණවුමට අදාළ භාණ්ඩ ලැයිස්තුව
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    // --- Getters and Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public Double getDeliveryCharge() { return deliveryCharge; }
    public void setDeliveryCharge(Double deliveryCharge) { this.deliveryCharge = deliveryCharge; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }

    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }

    public Double getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(Double discountAmount) { this.discountAmount = discountAmount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDateTime orderDate) { this.orderDate = orderDate; }

    public List<OrderItem> getOrderItems() { return orderItems; }
    public void setOrderItems(List<OrderItem> orderItems) { this.orderItems = orderItems; }
}