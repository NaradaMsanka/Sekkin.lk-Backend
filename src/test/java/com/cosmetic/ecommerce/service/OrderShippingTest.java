package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.controller.OrderController;
import com.cosmetic.ecommerce.dto.DirectOrderRequestDTO;
import com.cosmetic.ecommerce.entity.*;
import com.cosmetic.ecommerce.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OrderShippingTest {
    @Mock OrderRepository orders;
    @Mock ProductRepository products;
    @Mock UserRepository users;
    @Mock CartService carts;
    @Mock OrderPlacementService placement;
    @InjectMocks OrderService service;
    private final String email = "shipping@example.test";

    private Product product(long id, String price) {
        Product product = new Product(); product.setId(id); product.setName("Shipping fixture " + id);
        product.setPrice(new BigDecimal(price)); product.setStockQuantity(100);
        return product;
    }
    private Cart cart(Product product, Integer quantity) {
        Cart cart = new Cart(); User user = new User(); user.setEmail(email); cart.setUser(user);
        CartItem item = new CartItem(); item.setProduct(product); item.setQuantity(quantity);
        item.setPrice(0.0); cart.setTotalPrice(0.0); // Deliberately stale/manipulated cart amounts.
        cart.getItems().add(item); return cart;
    }
    private DirectOrderRequestDTO direct(Product product, Integer quantity, String coupon) {
        signedIn();
        DirectOrderRequestDTO request = new DirectOrderRequestDTO(); request.setProductId(product.getId());
        request.setQuantity(quantity); request.setPaymentMethod("COD"); request.setCouponCode(coupon);
        request.setEmail(email); request.setAddress("Test address"); return request;
    }
    private void signedIn() {
        User account = new User(); account.setEmail(email);
        when(users.findByEmailForUpdate(email)).thenReturn(account);
    }
    private void saveOrders() { when(orders.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0)); }

    @ParameterizedTest @CsvSource({"1000,1400", "5000,5400", "10000,10400"})
    void eachCheckoutCharges400OnceUsingCatalogPrices(String price, double expected) {
        Product product = product(1, price); when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(carts.getCart(email)).thenReturn(cart(product, 1)); saveOrders();
        Order normal = service.createOrder(email, "Test address");
        Order direct = service.createDirectOrder(direct(product, 1, null), email);
        for (Order order : List.of(normal, direct)) {
            assertEquals(400.0, order.getDeliveryCharge()); assertEquals(expected, order.getTotalAmount());
            assertEquals("PENDING_COD", order.getPaymentStatus());
        }
    }

    @Test void multipleProductsAndQuantitiesShareOneDeliveryFee() {
        Product first = product(1, "1000"), second = product(2, "1500");
        Cart cart = cart(first, 2); cart.getItems().add(cart(second, 2).getItems().getFirst());
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(first)); when(products.findByIdForUpdate(2L)).thenReturn(Optional.of(second));
        when(carts.getCart(email)).thenReturn(cart); saveOrders();
        Order order = service.createOrder(email, "Test address");
        assertEquals(5400.0, order.getTotalAmount()); assertEquals(400.0, order.getDeliveryCharge());
        assertEquals(2, order.getOrderItems().size());
    }

    @ParameterizedTest @CsvSource({"SAVE500,4900", "GLOW10,4900", "WELCOME10,4900", "unknown,5400"})
    void couponsOnlyDiscountMerchandiseForBothFlows(String coupon, double expected) {
        Product product = product(1, "2500"); when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(carts.getCart(email)).thenReturn(cart(product, 2)); saveOrders();
        Order normal = service.createOrder(email, "Test address", coupon);
        Order direct = service.createDirectOrder(direct(product, 2, coupon), email);
        assertEquals(expected, normal.getTotalAmount()); assertEquals(expected, direct.getTotalAmount());
        assertEquals(400.0, normal.getDeliveryCharge()); assertEquals(400.0, direct.getDeliveryCharge());
    }

    @Test void productAndCouponSavingsAreNotSubtractedTwice() {
        Product product = product(1, "3000"); product.setDiscountPrice(new BigDecimal("2500"));
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product)); saveOrders();
        Order order = service.createDirectOrder(direct(product, 2, "SAVE500"), email);
        assertEquals(4900.0, order.getTotalAmount()); assertEquals(1500.0, order.getDiscountAmount());
        assertEquals(2500.0, order.getOrderItems().getFirst().getPrice());
    }

    @Test void percentageRoundsToCentsAndDeliveryRemainsPayable() {
        Product product = product(1, "0.05");
        var totals = OrderPricing.calculate(List.of(new OrderPricing.Line(product, 1)), "GLOW10");
        assertEquals(new BigDecimal("0.01"), totals.couponDiscount());
        assertEquals(new BigDecimal("400.04"), totals.total());
    }

    @Test void emptyCartIsRejectedWithoutWrites() {
        when(carts.getCart(email)).thenReturn(new Cart());
        assertThrows(RuntimeException.class, () -> service.createOrder(email, "Test address"));
        verifyNoInteractions(orders, products); verify(carts, never()).clearCart(any());
    }

    @ParameterizedTest @NullSource @ValueSource(ints = {0, -1})
    void invalidQuantitiesAreRejectedInBothFlows(Integer quantity) {
        Product product = product(1, "1000"); when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(carts.getCart(email)).thenReturn(cart(product, quantity));
        assertThrows(RuntimeException.class, () -> service.createOrder(email, "Test address"));
        assertThrows(RuntimeException.class, () -> service.createDirectOrder(direct(product, quantity, null), email));
        verifyNoInteractions(orders); verify(products, never()).save(any());
    }

    @ParameterizedTest @ValueSource(strings = {"-1", "0", "0.004"})
    void invalidProductPriceIsRejectedWithoutStockChanges(String price) {
        Product product = product(1, price); when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        assertThrows(RuntimeException.class, () -> service.createDirectOrder(direct(product, 1, null), email));
        assertEquals(100, product.getStockQuantity()); verifyNoInteractions(orders);
    }

    @Test void combinedQuantitiesCannotExceedStockBeforeAnyWrites() {
        Product product = product(1, "1000"); product.setStockQuantity(3);
        Cart cart = cart(product, 2); cart.getItems().add(cart(product, 2).getItems().getFirst());
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product)); when(carts.getCart(email)).thenReturn(cart);
        assertThrows(RuntimeException.class, () -> service.createOrder(email, "Test address"));
        assertEquals(3, product.getStockQuantity()); verifyNoInteractions(orders); verify(products, never()).save(any());
    }

    @Test void forgedFrontendAmountsAreRejectedInsteadOfOverridingHttpOrderTotals() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new OrderController(service, placement)).build();
        mvc.perform(post("/api/orders").header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                .content("{\"shippingAddress\":\"Test\",\"deliveryCharge\":0,\"totalAmount\":1,\"price\":1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(placement, orders, products, carts);
    }

    @Test void historyReturnsOriginalStoredFeesAndTotals() throws Exception {
        Order old = new Order(); old.setId(50L); old.setShippingAddress("Test");
        old.setDeliveryCharge(0.0); old.setTotalAmount(1000.0); old.setOrderDate(LocalDateTime.of(2025, 1, 1, 0, 0));
        when(orders.findByUserEmail(email)).thenReturn(List.of(old));
        var mvc = MockMvcBuilders.standaloneSetup(new OrderController(service, placement)).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email, null, List.of()));
        try {
            mvc.perform(get("/api/orders")).andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].deliveryCharge").value(0.0)).andExpect(jsonPath("$[0].totalAmount").value(1000.0));
        } finally { SecurityContextHolder.clearContext(); }
        verify(orders, never()).save(any());
    }

    @Test void directHttpConfirmationMatchesStoredTotalsAndCouponBreakdown() throws Exception {
        signedIn();
        Product product = product(1, "2500"); when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product)); saveOrders();
        when(placement.placeDirect(any(), any(), any())).thenAnswer(call -> service.createDirectOrder(call.getArgument(1), email));
        var mvc = MockMvcBuilders.standaloneSetup(new OrderController(service, placement)).build();
        mvc.perform(post("/api/orders/direct").header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json").content("{\"productId\":1,\"quantity\":2,\"name\":\"Fixture\",\"phoneNumber\":\"0000000000\",\"email\":\"fixture@example.test\",\"address\":\"Test\",\"paymentMethod\":\"COD\",\"couponCode\":\"SAVE500\",\"expectedTotal\":4900}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deliveryCharge").value(400.0))
                .andExpect(jsonPath("$.subtotal").value(5000.0)).andExpect(jsonPath("$.couponDiscount").value(500.0))
                .andExpect(jsonPath("$.totalAmount").value(4900.0)).andExpect(jsonPath("$.paymentStatus").value("PENDING_COD"));
        var saved = ArgumentCaptor.forClass(Order.class); verify(orders).saveAndFlush(saved.capture());
        assertEquals(400.0, saved.getValue().getDeliveryCharge()); assertEquals(4900.0, saved.getValue().getTotalAmount());
    }

    @ParameterizedTest @CsvSource({"2499,0,2899", "2500,500,2400", "2501,500,2401"})
    void flatCouponThresholdNeverChangesDelivery(String subtotal, String discount, String total) {
        var amounts = OrderPricing.calculate(List.of(new OrderPricing.Line(product(1, subtotal), 1)), "SAVE500");
        assertEquals(new BigDecimal(discount).setScale(2), amounts.couponDiscount());
        assertEquals(new BigDecimal(total).setScale(2), amounts.total());
        assertEquals(new BigDecimal("400.00"), amounts.deliveryCharge());
    }
}
