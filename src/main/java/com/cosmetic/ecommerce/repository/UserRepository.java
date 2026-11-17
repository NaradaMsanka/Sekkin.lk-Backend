package com.cosmetic.ecommerce.repository;

import com.cosmetic.ecommerce.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email); // Email එක කලින්ම තියෙනවද බලන්න
    User findByEmail(String email);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.email = :email")
    User findByEmailForUpdate(@org.springframework.data.repository.query.Param("email") String email);
}
