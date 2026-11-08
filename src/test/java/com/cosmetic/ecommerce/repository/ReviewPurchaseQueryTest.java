package com.cosmetic.ecommerce.repository;

import com.cosmetic.ecommerce.entity.Order;
import com.cosmetic.ecommerce.entity.OrderItem;
import com.cosmetic.ecommerce.entity.Product;
import com.cosmetic.ecommerce.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

// Isolated in-memory database. No application startup runners or Supabase connection.
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"})
@ContextConfiguration(classes = ReviewPurchaseQueryTest.JpaConfig.class)
class ReviewPurchaseQueryTest {
    @Configuration @EntityScan(basePackageClasses = Order.class) @EnableJpaRepositories(basePackageClasses = OrderRepository.class)
    static class JpaConfig {}
    @Autowired TestEntityManager em;
    @Autowired OrderRepository orders;
    User buyer;
    Product product;

    @BeforeEach void setup() {
        buyer = new User(); buyer.setEmail("buyer@example.test"); em.persist(buyer);
        product = new Product(); product.setName("Test product"); product.setPrice(BigDecimal.TEN); product.setStockQuantity(10); em.persistAndFlush(product);
    }

    private void order(User owner, String status, int quantity, Product itemProduct) {
        Order order = new Order(); order.setUser(owner); order.setCustomerEmail(buyer.getEmail()); order.setStatus(status);
        OrderItem item = new OrderItem(); item.setOrder(order); item.setProduct(itemProduct); item.setQuantity(quantity); item.setPrice(10.0);
        order.getOrderItems().add(item); em.persistAndFlush(order); em.clear();
    }

    @ParameterizedTest @ValueSource(strings = {"DELIVERED", "COMPLETED", "delivered"})
    void completedOrdersQualify(String status) {
        order(buyer, status, 1, product);
        assertTrue(orders.hasCompletedPurchase(buyer.getId(), product.getId()));
    }

    @ParameterizedTest @ValueSource(strings = {"PENDING", "PROCESSING", "SHIPPED", "CANCELLED", "REFUNDED", "FAILED"})
    void unfinishedOrCancelledOrdersDoNotQualify(String status) {
        order(buyer, status, 1, product);
        assertFalse(orders.hasCompletedPurchase(buyer.getId(), product.getId()));
    }

    @Test void anotherUsersPurchaseDoesNotQualify() {
        User other = new User(); other.setEmail("other@example.test"); em.persistAndFlush(other);
        order(other, "DELIVERED", 1, product);
        assertFalse(orders.hasCompletedPurchase(buyer.getId(), product.getId()));
    }

    @Test void guestEmailAloneCannotClaimPurchase() {
        order(null, "DELIVERED", 1, product);
        assertFalse(orders.hasCompletedPurchase(buyer.getId(), product.getId()));
    }

    @Test void anotherProductDoesNotQualify() {
        Product other = new Product(); other.setName("Other"); other.setPrice(BigDecimal.TEN); other.setStockQuantity(1); em.persistAndFlush(other);
        order(buyer, "DELIVERED", 1, other);
        assertFalse(orders.hasCompletedPurchase(buyer.getId(), product.getId()));
    }

    @Test void zeroQuantityDoesNotQualify() {
        order(buyer, "DELIVERED", 0, product);
        assertFalse(orders.hasCompletedPurchase(buyer.getId(), product.getId()));
    }
}
