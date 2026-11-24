package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.dto.DirectOrderRequestDTO;
import com.cosmetic.ecommerce.entity.*;
import com.cosmetic.ecommerce.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Only a replaced, embedded H2 database. No application startup/admin/JWT or hosted database.
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"})
@ContextConfiguration(classes = OrderShippingPersistenceTest.PersistenceConfiguration.class)
class OrderShippingPersistenceTest {
    @Configuration
    @EnableJpaRepositories(basePackageClasses = OrderRepository.class)
    @EntityScan(basePackageClasses = Order.class)
    static class PersistenceConfiguration {}

    @Autowired OrderRepository orders;
    @Autowired ProductRepository products;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;

    @Test void savesNewFeesWithoutChangingHistoricalRows() {
        Order historical = new Order(); historical.setDeliveryCharge(0.0); historical.setTotalAmount(1000.0);
        long historicalId = orders.saveAndFlush(historical).getId();
        Product product = new Product(); product.setName("Isolated shipping fixture");
        product.setPrice(new BigDecimal("2500.00")); product.setStockQuantity(10);
        product = products.saveAndFlush(product);
        CartService carts = mock(CartService.class);
        OrderService service = new OrderService(orders, carts, products, users);
        DirectOrderRequestDTO request = new DirectOrderRequestDTO(); request.setProductId(product.getId());
        request.setQuantity(2); request.setPaymentMethod("COD"); request.setCouponCode("SAVE500");
        request.setEmail("fixture@example.test"); request.setAddress("Test address");
        Order direct = service.createDirectOrder(request, null);
        User user = new User(); user.setName("Fixture"); user.setEmail("cart@example.test");
        user.setPassword("synthetic-test-only"); user.setRole("USER"); user = users.saveAndFlush(user);
        Cart cart = new Cart(); cart.setUser(user); CartItem item = new CartItem();
        item.setProduct(product); item.setQuantity(2); cart.getItems().add(item);
        when(carts.getCart("cart@example.test")).thenReturn(cart);
        Order normal = service.createOrder("cart@example.test", "Test address", "SAVE500");
        orders.flush(); entityManager.clear();
        for (long id : new long[]{direct.getId(), normal.getId()}) {
            Order persisted = orders.findById(id).orElseThrow();
            assertEquals(400.0, persisted.getDeliveryCharge()); assertEquals(4900.0, persisted.getTotalAmount());
            assertEquals(500.0, persisted.getDiscountAmount()); assertEquals(2500.0, persisted.getOrderItems().getFirst().getPrice());
        }
        Order unchanged = orders.findById(historicalId).orElseThrow();
        assertEquals(0.0, unchanged.getDeliveryCharge()); assertEquals(1000.0, unchanged.getTotalAmount());
    }
}
