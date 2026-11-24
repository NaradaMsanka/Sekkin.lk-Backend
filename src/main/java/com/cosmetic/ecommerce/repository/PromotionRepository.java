package com.cosmetic.ecommerce.repository;

import com.cosmetic.ecommerce.entity.Promotion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    Optional<Promotion> findByCode(String code);
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from Promotion p where p.code = :code")
    Optional<Promotion> findForPricing(@Param("code") String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Promotion p where p.id = :id")
    Optional<Promotion> findForUpdate(@Param("id") Long id);
}
