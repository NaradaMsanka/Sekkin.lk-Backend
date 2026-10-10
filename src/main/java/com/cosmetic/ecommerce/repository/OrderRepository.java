package com.cosmetic.ecommerce.repository;

import com.cosmetic.ecommerce.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserEmail(String email); // User ගේ Orders හොයාගන්න
}