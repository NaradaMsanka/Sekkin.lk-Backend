package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.dto.DirectOrderRequestDTO;
import com.cosmetic.ecommerce.entity.*;
import com.cosmetic.ecommerce.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class OrderService {
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PromotionService promotions;

    public OrderService(OrderRepository orders, CartService carts, ProductRepository products, UserRepository users, PromotionService promotions) {
        orderRepository = orders; cartService = carts; productRepository = products; userRepository = users;
        this.promotions = promotions;
    }

    @Transactional
    public Order createOrder(String email, String address) { return createOrder(email, address, null); }

    @Transactional
    public Order createOrder(String email, String address, String coupon) {
        return createOrder(email, address, coupon, null, null, null);
    }

    @Transactional
    public Order createOrder(String email, String address, String coupon, BigDecimal expectedTotal, String keyHash, String fingerprint) {
        return createOrder(email, address, coupon, expectedTotal, keyHash, fingerprint, null);
    }

    @Transactional
    public Order createOrder(String email, String address, String coupon, BigDecimal expectedTotal, String keyHash, String fingerprint,
            List<com.cosmetic.ecommerce.dto.CartOrderRequestDTO.CartLine> expectedItems) {
        return createOrder(email, address, coupon, expectedTotal, keyHash, fingerprint, expectedItems, null);
    }

    @Transactional
    public Order createOrder(String email, String address, String coupon, BigDecimal expectedTotal, String keyHash, String fingerprint,
            List<com.cosmetic.ecommerce.dto.CartOrderRequestDTO.CartLine> expectedItems, String customerPhone) {
        if (address == null || address.isBlank()) throw bad("Shipping address is required");
        // Every cart mutation locks its owning user first. Checkout shares that lock until commit.
        Cart cart = cartService.getCart(email);
        if (cart.getItems() == null || cart.getItems().isEmpty()) throw bad("Cannot place order. Cart is empty.");
        List<Long> ids = new ArrayList<>();
        for (CartItem item : cart.getItems()) {
            if (item == null || item.getProduct() == null || item.getProduct().getId() == null) throw bad("Invalid cart product");
            ids.add(item.getProduct().getId());
        }
        Map<Long, Product> locked = lockProducts(ids);
        if (expectedItems != null) {
            Map<Long, Long> actual = new TreeMap<>();
            for (CartItem item : cart.getItems()) actual.merge(item.getProduct().getId(), item.getQuantity().longValue(), Long::sum);
            Map<Long, Long> expected = new TreeMap<>();
            for (var item : expectedItems) expected.merge(item.productId(), item.quantity().longValue(), Long::sum);
            if (!actual.equals(expected)) throw conflict("Cart contents changed. Review the cart before ordering.");
        }
        List<OrderPricing.Line> lines = cart.getItems().stream()
                .map(item -> new OrderPricing.Line(locked.get(item.getProduct().getId()), item.getQuantity())).toList();
        OrderPricing.Totals totals = priceAndValidateStock(lines, coupon);
        verifyExpectedTotal(expectedTotal, totals.total());
        Order order = newOrder(keyHash, fingerprint);
        order.setUser(cart.getUser()); order.setShippingAddress(address);
        order.setCustomerName(cart.getUser().getName()); order.setCustomerEmail(cart.getUser().getEmail()); order.setCustomerPhone(customerPhone);
        applyTotals(order, totals); reserveItems(order, lines);
        Order saved = orderRepository.saveAndFlush(order);
        cartService.clearCart(email);
        return saved;
    }

    @Transactional
    public Order createDirectOrder(DirectOrderRequestDTO request, String email) {
        return createDirectOrder(request, email, null, null);
    }

    @Transactional
    public Order createDirectOrder(DirectOrderRequestDTO request, String email, String keyHash, String fingerprint) {
        if (email == null || email.isBlank() || "anonymousUser".equals(email))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required");
        String method = request.getPaymentMethod() == null ? "" : request.getPaymentMethod().trim().toUpperCase(Locale.ROOT);
        if (!"COD".equals(method)) throw bad("Online payments are not enabled. Use cash on delivery.");
        if (request.getProductId() == null) throw bad("Product ID is required");
        User account = userRepository.findByEmailForUpdate(email);
        if (account == null || !account.isEnabled()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account unavailable");
        // Ownership always comes from the signed-in account, never the contact email in the request.
        Product product = lockProducts(List.of(request.getProductId())).get(request.getProductId());
        List<OrderPricing.Line> lines = List.of(new OrderPricing.Line(product, request.getQuantity()));
        OrderPricing.Totals totals = priceAndValidateStock(lines, request.getCouponCode());
        verifyExpectedTotal(request.getExpectedTotal(), totals.total());
        Order order = newOrder(keyHash, fingerprint);
        order.setUser(account); order.setCustomerName(request.getName()); order.setCustomerPhone(request.getPhoneNumber());
        order.setCustomerEmail(request.getEmail()); order.setShippingAddress(request.getAddress()); order.setPostalCode(request.getPostalCode());
        applyTotals(order, totals); reserveItems(order, lines);
        return orderRepository.saveAndFlush(order);
    }

    private Order newOrder(String keyHash, String fingerprint) {
        Order order = new Order(); order.setStatus("PENDING"); order.setPaymentMethod("COD");
        order.setPaymentStatus("PENDING_COD"); order.setInventoryReserved(true);
        order.setRequestKeyHash(keyHash); order.setRequestFingerprint(fingerprint); return order;
    }

    private Map<Long, Product> lockProducts(Collection<Long> ids) {
        Map<Long, Product> products = new LinkedHashMap<>();
        // One lock order prevents opposing multi-product checkouts from deadlocking each other.
        ids.stream().distinct().sorted().forEach(id -> {
            Product product = productRepository.findByIdForUpdate(id).orElseThrow(() -> bad("Product not found"));
            // Cart loading can have populated the persistence context before this row lock.
            if (entityManager != null) entityManager.refresh(product, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            products.put(id, product);
        });
        return products;
    }

    private void reserveItems(Order order, List<OrderPricing.Line> lines) {
        for (OrderPricing.Line line : lines) {
            Product product = line.product(); product.setStockQuantity(product.getStockQuantity() - line.quantity());
            productRepository.save(product);
            OrderItem item = new OrderItem(); item.setOrder(order); item.setProduct(product);
            item.setQuantity(line.quantity()); item.setPrice(storedMoney(OrderPricing.unitPrice(product))); order.getOrderItems().add(item);
        }
    }

    private OrderPricing.Totals priceAndValidateStock(List<OrderPricing.Line> lines, String coupon) {
        OrderPricing.Totals totals;
        try { totals = OrderPricing.calculate(lines, PromotionService.normalize(coupon) == null ? null : promotions.resolve(coupon)); }
        catch (IllegalArgumentException error) { throw bad(error.getMessage()); }
        // Preserve the existing monetary schema without silently losing cents on unsupported large values.
        for (BigDecimal amount : List.of(totals.subtotal(), totals.savings(), totals.couponDiscount(), totals.deliveryCharge(), totals.total())) storedMoney(amount);
        for (OrderPricing.Line line : lines) storedMoney(OrderPricing.unitPrice(line.product()));
        Map<Long, Long> required = new HashMap<>();
        for (OrderPricing.Line line : lines) {
            long quantity = required.merge(line.product().getId(), (long) line.quantity(), Long::sum);
            Integer stock = line.product().getStockQuantity();
            if (stock == null || stock < quantity) throw new ResponseStatusException(HttpStatus.CONFLICT, "Not enough stock for product: " + line.product().getName());
        }
        return totals;
    }

    private void verifyExpectedTotal(BigDecimal expected, BigDecimal actual) {
        if (expected == null) return; // Internal legacy callers only; HTTP placement requires the field.
        if ((long) expected.precision() - expected.scale() > 15 || expected.scale() > 2) throw bad("Displayed order total is invalid");
        try {
            if (expected.signum() <= 0 || expected.setScale(2, RoundingMode.UNNECESSARY).compareTo(actual) != 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Order total changed. Refresh prices and confirm the new total before ordering.");
        } catch (ArithmeticException error) { throw bad("Displayed order total must have at most two decimal places"); }
    }

    private void applyTotals(Order order, OrderPricing.Totals totals) {
        order.setDeliveryCharge(storedMoney(totals.deliveryCharge())); order.setCouponCode(totals.couponCode());
        order.setDiscountAmount(storedMoney(totals.savings())); order.setTotalAmount(storedMoney(totals.total()));
        order.setCouponPercentage(totals.couponPercentage()); order.setCouponDiscount(storedMoney(totals.couponDiscount()));
    }

    private double storedMoney(BigDecimal amount) {
        double value = amount.doubleValue();
        if (!Double.isFinite(value) || BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).compareTo(amount) != 0)
            throw bad("Catalog pricing exceeds supported precision; contact support");
        return value;
    }

    @Transactional(readOnly = true)
    public List<Order> getUserOrders(String email) { return orderRepository.findByUserEmail(email); }

    @Transactional(readOnly = true)
    public Order getOrderById(Long id, String email) {
        Order order = orderRepository.findById(id).orElseThrow(() -> missing());
        if (email == null || email.isBlank() || order.getUser() == null || !email.equals(order.getUser().getEmail()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized to view this order");
        return order;
    }

    @Transactional
    public Order updateOrderStatus(Long id, String requested) {
        if (requested == null || requested.isBlank()) throw bad("Status value is required");
        String target = requested.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PENDING", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED").contains(target)) throw bad("Unsupported fulfilment status");
        Order order = lockedOrder(id);
        String source = order.getStatus();
        if (target.equals(source)) return order;
        boolean allowed = ("PENDING".equals(source) && "CANCELLED".equals(target))
                || ("PROCESSING".equals(source) && Set.of("SHIPPED", "CANCELLED").contains(target))
                || ("SHIPPED".equals(source) && "DELIVERED".equals(target));
        if (!allowed) throw conflict("Unsupported order transition; acceptance requires its confirmation endpoint and dispatched orders cannot be cancelled ordinarily.");
        if ("CANCELLED".equals(target) && Boolean.TRUE.equals(order.getInventoryReserved())) restoreInventory(order);
        order.setStatus(target); return orderRepository.save(order);
    }

    @Transactional
    public Order acceptOrder(Long id, String reference, String operator) {
        reference = requireReference(reference); requireOperator(operator);
        Order order = lockedOrder(id);
        if ("PROCESSING".equals(order.getStatus()) && order.getAcceptedAt() != null && reference.equals(order.getAcceptanceReference())) return order;
        if (!"PENDING".equals(order.getStatus())) throw conflict("Only a pending order request can be accepted");
        if (!Boolean.TRUE.equals(order.getInventoryReserved())) throw conflict("Historical inventory reservation needs manual review before acceptance");
        if (!"COD".equals(order.getPaymentMethod()) || !"PENDING_COD".equals(order.getPaymentStatus()))
            throw conflict("Historical payment state needs manual review before acceptance");
        order.setStatus("PROCESSING"); order.setAcceptedAt(LocalDateTime.now()); order.setAcceptedBy(operator); order.setAcceptanceReference(reference);
        return orderRepository.save(order);
    }

    @Transactional
    public Order recordCodCollection(Long id, BigDecimal amount, String reference, String operator) {
        reference = requireReference(reference); requireOperator(operator);
        Order order = lockedOrder(id);
        if (amount == null || order.getTotalAmount() == null || !Double.isFinite(order.getTotalAmount())) throw bad("Collection amount is required");
        if ((long) amount.precision() - amount.scale() > 15 || amount.scale() > 2) throw bad("Collection amount is invalid");
        BigDecimal recorded;
        try { recorded = amount.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException error) { throw bad("Collection amount must have at most two decimal places"); }
        if (recorded.signum() <= 0 || recorded.compareTo(BigDecimal.valueOf(order.getTotalAmount()).setScale(2, RoundingMode.HALF_UP)) != 0)
            throw conflict("Collection amount must exactly match the stored order total");
        if (!"COD".equals(order.getPaymentMethod()) || !"DELIVERED".equals(order.getStatus())) throw conflict("Only a delivered COD order can have its collection verified");
        if ("PAID".equals(order.getPaymentStatus()) && order.getCodCollectedAt() != null && reference.equals(order.getCodCollectionReference())) return order;
        if (!"PENDING_COD".equals(order.getPaymentStatus())) throw conflict("Payment state needs manual review");
        order.setPaymentStatus("PAID"); order.setCodCollectedAt(LocalDateTime.now()); order.setCodCollectedBy(operator); order.setCodCollectionReference(reference);
        return orderRepository.save(order);
    }

    private void restoreInventory(Order order) {
        Map<Long, Long> quantities = new TreeMap<>();
        for (OrderItem item : order.getOrderItems()) {
            if (item.getProduct() == null || item.getProduct().getId() == null || item.getQuantity() == null || item.getQuantity() <= 0)
                throw conflict("Reserved inventory needs manual review");
            quantities.merge(item.getProduct().getId(), item.getQuantity().longValue(), Long::sum);
        }
        Map<Long, Product> products = lockProducts(quantities.keySet());
        for (var entry : quantities.entrySet()) {
            Product product = products.get(entry.getKey());
            if (product.getStockQuantity() == null || product.getStockQuantity() < 0 || entry.getValue() + product.getStockQuantity() > Integer.MAX_VALUE)
                throw conflict("Reserved inventory needs manual review");
            product.setStockQuantity((int) (product.getStockQuantity() + entry.getValue())); productRepository.save(product);
        }
        order.setInventoryReserved(false);
    }

    private Order lockedOrder(Long id) { return orderRepository.findByIdForUpdate(id).orElseThrow(() -> missing()); }
    private String requireReference(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 160 || value.chars().anyMatch(Character::isISOControl))
            throw bad("A nonsecret confirmation reference of 1–160 characters is required");
        return value.trim();
    }
    private void requireOperator(String operator) { if (operator == null || operator.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Operator required"); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"); }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
