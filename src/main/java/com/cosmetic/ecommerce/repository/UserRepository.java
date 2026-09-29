package com.cosmetic.ecommerce.repository;

import com.cosmetic.ecommerce.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email); // Email එක කලින්ම තියෙනවද බලන්න
}