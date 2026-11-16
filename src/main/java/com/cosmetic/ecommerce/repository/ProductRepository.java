package com.cosmetic.ecommerce.repository;

import com.cosmetic.ecommerce.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    java.util.Optional<Product> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT p FROM Product p WHERE " +
            "(:brand IS NULL OR p.brand = :brand) AND " +
            "(:skinType IS NULL OR p.skinType = :skinType) AND " +
            "(:minPrice IS NULL OR p.price >= :minPrice) AND " +
            "(:maxPrice IS NULL OR p.price <= :maxPrice)")
    List<Product> searchAndFilterProducts(@Param("brand") String brand,
                                          @Param("skinType") String skinType,
                                          @Param("minPrice") Double minPrice,
                                          @Param("maxPrice") Double maxPrice);

    // Day 24: Admin Dashboard සඳහා අඩු තොග (Low-stock) භාණ්ඩ සෙවීමට
    List<Product> findByStockQuantityLessThanEqual(Integer threshold);
}
