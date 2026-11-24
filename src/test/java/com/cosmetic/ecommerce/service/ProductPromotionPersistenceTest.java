package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.dto.*;
import com.cosmetic.ecommerce.entity.*;
import com.cosmetic.ecommerce.repository.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"})
@ContextConfiguration(classes = ProductPromotionPersistenceTest.Config.class)
class ProductPromotionPersistenceTest {
    @Configuration @EnableJpaRepositories(basePackageClasses = OrderRepository.class) @EntityScan(basePackageClasses = Order.class)
    @Import({OrderService.class, CartService.class, PromotionService.class, CheckoutQuoteService.class}) static class Config {}
    @Autowired PromotionService promotions; @Autowired CheckoutQuoteService quotes; @Autowired OrderService service;
    @Autowired ProductRepository products; @Autowired UserRepository users; @Autowired OrderRepository orders; @Autowired EntityManager entityManager;

    private Product product(String name, String price) {
        Product product = new Product(); product.setName(name); product.setPrice(new BigDecimal(price)); product.setStockQuantity(100);
        return products.saveAndFlush(product);
    }
    private PromotionRequest offer(String code, String rate, boolean active, Long... ids) {
        return new PromotionRequest(code, new BigDecimal(rate), active, Set.of(ids));
    }
    private void bad(Runnable action) { assertEquals(400, assertThrows(ResponseStatusException.class, action::run).getStatusCode().value()); }

    @Test void numericPromoCodeDiscountsOnlySelectedProductsInAMixedCart() {
        Product selected = product("Selected", "1000"), excluded = product("Excluded", "500");
        promotions.save(null, offer("424", "12.50", true, selected.getId()));
        var totals = quotes.quote(List.of(new CartOrderRequestDTO.CartLine(selected.getId(), 2), new CartOrderRequestDTO.CartLine(excluded.getId(), 1)), " 424 ");
        assertEquals(new BigDecimal("2500.00"), totals.subtotal()); assertEquals(new BigDecimal("250.00"), totals.couponDiscount());
        assertEquals(new BigDecimal("400.00"), totals.deliveryCharge()); assertEquals(new BigDecimal("2650.00"), totals.total());
        assertEquals("424", totals.couponCode()); assertEquals(new BigDecimal("12.50"), totals.couponPercentage());
        assertEquals(100, products.findById(selected.getId()).orElseThrow().getStockQuantity()); assertEquals(0, orders.count());
    }

    @Test void unknownInactiveAndIneligibleCodesAreRejectedWithoutReservingStock() {
        Product selected = product("Selected", "1000"), excluded = product("Excluded", "500");
        promotions.save(null, offer("ACTIVE10", "10", true, selected.getId()));
        promotions.save(null, offer("DISABLED10", "10", false, selected.getId()));
        var selectedItems = List.of(new CartOrderRequestDTO.CartLine(selected.getId(), 1));
        bad(() -> quotes.quote(selectedItems, "UNKNOWN")); bad(() -> quotes.quote(selectedItems, "DISABLED10"));
        bad(() -> quotes.quote(List.of(new CartOrderRequestDTO.CartLine(excluded.getId(), 1)), "ACTIVE10"));
        assertEquals(0, orders.count()); assertEquals(100, products.findById(selected.getId()).orElseThrow().getStockQuantity());
    }

    @Test void selectedProductsAndValidRatesAreRequiredAndCodesAreUniqueIgnoringCase() {
        Product selected = product("Selected", "1000");
        bad(() -> promotions.save(null, offer("ZERO", "0", true, selected.getId())));
        bad(() -> promotions.save(null, offer("HIGH", "100.01", true, selected.getId())));
        bad(() -> promotions.save(null, offer("PRECISE", "10.001", true, selected.getId())));
        bad(() -> promotions.save(null, offer("NO_PRODUCTS", "10", true)));
        bad(() -> promotions.save(null, offer("MISSING", "10", true, Long.MAX_VALUE)));
        promotions.save(null, offer("CUSTOM10", "10", true, selected.getId()));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> promotions.save(null, offer(" custom10 ", "20", true, selected.getId()))).getStatusCode().value());
    }

    @Test void percentageUsesCurrentSalePriceRoundsToCentsAndNeverDiscountsDelivery() {
        Product selected = product("Selected", "1000"); selected.setDiscountPrice(new BigDecimal("800")); products.saveAndFlush(selected);
        promotions.save(null, offer("FULL", "100", true, selected.getId()));
        var totals = quotes.quote(List.of(new CartOrderRequestDTO.CartLine(selected.getId(), 2)), "full");
        assertEquals(new BigDecimal("1600.00"), totals.couponDiscount()); assertEquals(new BigDecimal("400.00"), totals.total());
        Product small = product("Small", "0.05"); promotions.save(null, offer("ROUND10", "10", true, small.getId()));
        var rounded = quotes.quote(List.of(new CartOrderRequestDTO.CartLine(small.getId(), 1)), "ROUND10");
        assertEquals(new BigDecimal("0.01"), rounded.couponDiscount()); assertEquals(new BigDecimal("400.04"), rounded.total());
    }

    @Test void persistedOrderRetainsCodeRateAndSavingsAfterAdminEditsThePromotion() {
        Product selected = product("Selected", "1000"); User account = new User(); account.setName("Fixture"); account.setEmail("promo-buyer@example.test");
        account.setPassword("synthetic-only"); account.setRole("USER"); account = users.saveAndFlush(account);
        Promotion promotion = promotions.save(null, offer("LOCK20", "20", true, selected.getId()));
        DirectOrderRequestDTO request = new DirectOrderRequestDTO(); request.setProductId(selected.getId()); request.setQuantity(1);
        request.setPaymentMethod("COD"); request.setEmail("contact@example.test"); request.setName("Fixture"); request.setPhoneNumber("0000000000");
        request.setAddress("Synthetic address"); request.setCouponCode("LOCK20"); request.setExpectedTotal(new BigDecimal("1200"));
        long id = service.createDirectOrder(request, account.getEmail()).getId();
        promotions.save(promotion.getId(), offer("LOCK20", "50", false, selected.getId())); entityManager.flush(); entityManager.clear();
        Order stored = orders.findById(id).orElseThrow();
        assertEquals("LOCK20", stored.getCouponCode()); assertEquals(new BigDecimal("20.00"), stored.getCouponPercentage());
        assertEquals(200.0, stored.getCouponDiscount()); assertEquals(1200.0, stored.getTotalAmount()); assertEquals(400.0, stored.getDeliveryCharge());
    }
}
