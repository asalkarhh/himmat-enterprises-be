package com.asalkartechworks.himmat.catalog.repository;

import com.asalkartechworks.himmat.catalog.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT p FROM Product p WHERE p.brand.id = :brandId AND p.subcategory.id = :subcategoryId AND LOWER(TRIM(p.name)) = LOWER(TRIM(:name))")
    Optional<Product> findByBrandIdAndSubcategoryIdAndNameIgnoreCase(
            @Param("brandId") Long brandId,
            @Param("subcategoryId") Long subcategoryId,
            @Param("name") String name);

    @Query("SELECT COUNT(p) > 0 FROM Product p WHERE p.brand.id = :brandId AND p.subcategory.id = :subcategoryId AND LOWER(TRIM(p.name)) = LOWER(TRIM(:name))")
    boolean existsByBrandIdAndSubcategoryIdAndNameIgnoreCase(
            @Param("brandId") Long brandId,
            @Param("subcategoryId") Long subcategoryId,
            @Param("name") String name);

    @Query("SELECT COUNT(p) > 0 FROM Product p WHERE p.brand.id = :brandId AND p.subcategory.id = :subcategoryId AND LOWER(TRIM(p.name)) = LOWER(TRIM(:name)) AND p.id != :id")
    boolean existsByBrandIdAndSubcategoryIdAndNameIgnoreCaseAndIdNot(
            @Param("brandId") Long brandId,
            @Param("subcategoryId") Long subcategoryId,
            @Param("name") String name,
            @Param("id") Long id);

    @Query("SELECT p FROM Product p JOIN FETCH p.brand JOIN FETCH p.subcategory sub JOIN FETCH sub.category WHERE " +
           "(:brandId IS NULL OR p.brand.id = :brandId) AND " +
           "(:subcategoryId IS NULL OR p.subcategory.id = :subcategoryId) AND " +
           "(:categoryId IS NULL OR p.subcategory.category.id = :categoryId) AND " +
           "(:isActive IS NULL OR p.isActive = :isActive) AND " +
           "(:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Product> searchProducts(@Param("brandId") Long brandId,
                                @Param("categoryId") Long categoryId,
                                @Param("subcategoryId") Long subcategoryId,
                                @Param("search") String search,
                                @Param("isActive") Boolean isActive,
                                Pageable pageable);
}
