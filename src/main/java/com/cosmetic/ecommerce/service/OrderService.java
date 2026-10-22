package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.dto.DirectOrderRequestDTO;
import com.cosmetic.ecommerce.entity.*;
import com.cosmetic.ecommerce.repository.OrderRepository;
import com.cosmetic.ecommerce.repository.ProductRepository;
import com.cosmetic.ecommerce.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public OrderService(OrderRepository orderRepository,
                        CartService cartService,
                        ProductRepository productRepository,
                        UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.cartService = cartService;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    // පැරණි OrderController කේතය වෙනස් නොකර දිගටම වැඩ කිරීමට (Coupon නොමැතිව එවන විට)
    @Transactional
    public Order createOrder(String email, String shippingAddress) {
        return createOrder(email, shippingAddress, null);
    }

    // Day 26: Product Discount Price සහ Coupon Code අදාළ කරමින් Order එක සෑදීම
    @Transactional
    public Order createOrder(String email, String shippingAddress, String couponCode) {
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
        order.setStatus("PENDING");

        double subtotal = 0.0;
        double totalDiscountSaved = 0.0;

        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();

            if (product.getStockQuantity() < cartItem.getQuantity()) {
                throw new RuntimeException("Not enough stock for product: " + product.getName());
            }

            // තොග ප්‍රමාණය (Stock) අඩු කිරීම
            product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());
            productRepository.save(product);

            // Day 26: භාණ්ඩයට discountPrice එකක් දමා ඇත්නම් එය ලබා ගැනීම
            double regularPrice = product.getPrice().doubleValue();
            double checkoutUnitPrice = product.getEffectivePrice().doubleValue();

            // භාණ්ඩයේ වට්ටම නිසා පාරිභෝගිකයාට ඉතිරි වූ මුදල ගණනය කිරීම
            if (checkoutUnitPrice < regularPrice) {
                totalDiscountSaved += (regularPrice - checkoutUnitPrice) * cartItem.getQuantity();
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setPrice(checkoutUnitPrice); // වට්ටම් සහිත මිල OrderItem එකේ සටහන් කිරීම

            order.getOrderItems().add(orderItem);
            subtotal += checkoutUnitPrice * cartItem.getQuantity();
        }

        // Day 26 (Optional): Simple Coupon Code පරීක්ෂාව
        double couponDiscount = 0.0;
        if (couponCode != null && !couponCode.trim().isEmpty()) {
            String code = couponCode.trim().toUpperCase();
            order.setCouponCode(code);

            if (code.equals("GLOW10") || code.equals("WELCOME10")) {
                couponDiscount = subtotal * 0.10; // මුළු මුදලෙන් 10% ක වට්ටමක්
            } else if (code.equals("SAVE500") && subtotal >= 2500) {
                couponDiscount = 500.0; // රු. 2500 ට වැඩි නම් රු. 500 ක වට්ටමක්
            }
        }

        // මුළු වට්ටම් මුදල සහ ගෙවිය යුතු අවසාන මුදල (Total Amount) Order එකට ලබා දීම
        double finalTotal = Math.max(0.0, subtotal - couponDiscount);
        order.setDiscountAmount(totalDiscountSaved + couponDiscount);
        order.setTotalAmount(finalTotal);

        Order savedOrder = orderRepository.save(order);
        cartService.clearCart(email); // Order එක සාර්ථක නිසා Cart එක හිස් කරනවා

        return savedOrder;
    }

    // Direct Instant Checkout without Cart (Handles CARD, KOKO, COD, Delivery Charge 350/= and Email Receipt)
    @Transactional
    public Order createDirectOrder(DirectOrderRequestDTO req, String loggedInEmail) {
        if (req.getProductId() == null) {
            throw new RuntimeException("Product ID is required");
        }
        Product product = productRepository.findById(req.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + req.getProductId()));

        int qty = req.getQuantity() != null && req.getQuantity() > 0 ? req.getQuantity() : 1;
        if (product.getStockQuantity() < qty) {
            throw new RuntimeException("Not enough stock for " + product.getName() + ". Only " + product.getStockQuantity() + " available.");
        }

        // Deduct stock quantity
        product.setStockQuantity(product.getStockQuantity() - qty);
        productRepository.save(product);

        // Price calculation with product discount
        double regularPrice = product.getPrice().doubleValue();
        double unitPrice = product.getEffectivePrice().doubleValue();
        double subtotal = unitPrice * qty;
        double totalDiscountSaved = (regularPrice > unitPrice) ? (regularPrice - unitPrice) * qty : 0.0;

        // Delivery charge rule: CARD and KOKO = 350/=, COD = 0.0
        double deliveryCharge = 0.0;
        String method = req.getPaymentMethod() != null ? req.getPaymentMethod().trim().toUpperCase() : "COD";
        if ("CARD".equals(method) || "KOKO".equals(method)) {
            deliveryCharge = 350.0;
        }

        // Optional coupon code
        double couponDiscount = 0.0;
        String couponCode = req.getCouponCode() != null ? req.getCouponCode().trim().toUpperCase() : null;
        if (couponCode != null && !couponCode.isEmpty()) {
            if ("GLOW10".equals(couponCode) || "WELCOME10".equals(couponCode)) {
                couponDiscount = subtotal * 0.10;
            } else if ("SAVE500".equals(couponCode) && subtotal >= 2500) {
                couponDiscount = 500.0;
            }
        }

        double finalTotal = Math.max(0.0, (subtotal + deliveryCharge) - couponDiscount);

        Order order = new Order();
        order.setCustomerName(req.getName());
        order.setCustomerPhone(req.getPhoneNumber());
        order.setCustomerEmail(req.getEmail());
        order.setShippingAddress(req.getAddress());
        order.setPostalCode(req.getPostalCode());
        order.setPaymentMethod(method);
        order.setDeliveryCharge(deliveryCharge);
        order.setCouponCode(couponCode);
        order.setDiscountAmount(totalDiscountSaved + couponDiscount);
        order.setTotalAmount(finalTotal);
        order.setStatus("PROCESSING");

        if ("CARD".equals(method)) {
            order.setPaymentStatus("PAID");
        } else if ("KOKO".equals(method)) {
            order.setPaymentStatus("KOKO_APPROVED");
        } else {
            order.setPaymentStatus("PENDING_COD");
        }

        // Link with user if logged in or if registered email matches
        String emailToSearch = (loggedInEmail != null && !loggedInEmail.isEmpty() && !"anonymousUser".equals(loggedInEmail))
                ? loggedInEmail : req.getEmail();
        if (emailToSearch != null && !emailToSearch.isEmpty()) {
            User user = userRepository.findByEmail(emailToSearch);
            if (user != null) {
                order.setUser(user);
            }
        }

        // Add Order Item
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(qty);
        item.setPrice(unitPrice);
        order.getOrderItems().add(item);

        Order savedOrder = orderRepository.save(order);
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

    // Admin සඳහා Order Status එක වෙනස් කිරීම
    public Order updateOrderStatus(Long orderId, String newStatus) {
        // Bug Fix: newStatus == null නම් NullPointerException ලැබෙයි toUpperCase() call කරද්දී
        if (newStatus == null || newStatus.trim().isEmpty()) {
            throw new RuntimeException("Status value is required");
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        order.setStatus(newStatus.toUpperCase()); // "SHIPPED", "DELIVERED" ලෙස ලොකු අකුරින් Save වීමට
        return orderRepository.save(order);
    }
}