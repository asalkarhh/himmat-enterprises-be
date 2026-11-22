package com.asalkartechworks.himmat.catalog.repository;

import com.asalkartechworks.himmat.catalog.model.ProductVariant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    @Query("SELECT v FROM ProductVariant v JOIN FETCH v.product p JOIN FETCH p.brand JOIN FETCH p.subcategory sub JOIN FETCH sub.category WHERE v.id = :id")
    Optional<ProductVariant> findByIdWithHierarchy(@Param("id") Long id);

    Optional<ProductVariant> findBySku(String sku);

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, Long id);

    @Query("SELECT COUNT(v) > 0 FROM ProductVariant v WHERE v.product.id = :productId AND LOWER(TRIM(v.sizeLabel)) = LOWER(TRIM(:sizeLabel))")
    boolean existsByProductIdAndSizeLabelIgnoreCase(@Param("productId") Long productId, @Param("sizeLabel") String sizeLabel);

    @Query("SELECT COUNT(v) > 0 FROM ProductVariant v WHERE v.product.id = :productId AND LOWER(TRIM(v.sizeLabel)) = LOWER(TRIM(:sizeLabel)) AND v.id != :id")
    boolean existsByProductIdAndSizeLabelIgnoreCaseAndIdNot(@Param("productId") Long productId, @Param("sizeLabel") String sizeLabel, @Param("id") Long id);

    @Query("SELECT v FROM ProductVariant v JOIN FETCH v.product p JOIN FETCH p.brand JOIN FETCH p.subcategory sub JOIN FETCH sub.category WHERE " +
           "(:productId IS NULL OR v.product.id = :productId) AND " +
           "(:isActive IS NULL OR v.isActive = :isActive) AND " +
           "(:search IS NULL OR LOWER(v.sku) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(v.sizeLabel) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<ProductVariant> searchVariants(@Param("productId") Long productId,
                                       @Param("search") String search,
                                       @Param("isActive") Boolean isActive,
                                       Pageable pageable);
}
