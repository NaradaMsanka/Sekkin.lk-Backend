package com.cosmetic.ecommerce.repository;

import com.cosmetic.ecommerce.entity.Order;
import com.cosmetic.ecommerce.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserEmail(String email); // User ගේ Orders හොයාගන්න
    List<Order> findByUser(User user);         // ReviewService සඳහා

    @Query("""
            select count(item) > 0 from OrderItem item
            join item.order purchase
            where purchase.user.id = :userId
              and item.product.id = :productId
              and item.quantity > 0
              and upper(purchase.status) in ('DELIVERED', 'COMPLETED')
            """)
    boolean hasCompletedPurchase(@Param("userId") Long userId, @Param("productId") Long productId);

    // Day 24: Admin Dashboard සඳහා අලුත්ම ඇණවුම් 5 (Recent Orders) ලබා ගැනීමට
    List<Order> findTop5ByOrderByOrderDateDesc();
}
