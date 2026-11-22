package com.asalkartechworks.himmat.catalog.repository;

import com.asalkartechworks.himmat.catalog.model.InventoryBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, Long> {

    Optional<InventoryBalance> findByVariantId(Long variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ib FROM InventoryBalance ib " +
           "JOIN FETCH ib.variant v " +
           "JOIN FETCH v.product p " +
           "WHERE ib.variantId = :variantId")
    Optional<InventoryBalance> findByVariantIdWithLock(@Param("variantId") Long variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ib FROM InventoryBalance ib " +
           "JOIN FETCH ib.variant v " +
           "JOIN FETCH v.product p " +
           "WHERE ib.variantId IN :variantIds ORDER BY ib.variantId ASC")
    List<InventoryBalance> findAllByVariantIdInOrderByIdAscWithLock(@Param("variantIds") Collection<Long> variantIds);

    @Query("SELECT ib FROM InventoryBalance ib " +
           "JOIN FETCH ib.variant v " +
           "JOIN FETCH v.product p " +
           "JOIN FETCH p.brand b " +
           "JOIN FETCH p.subcategory sub " +
           "JOIN FETCH sub.category cat " +
           "WHERE ib.variantId = :variantId")
    Optional<InventoryBalance> findByVariantIdWithHierarchy(@Param("variantId") Long variantId);

    @Query(value = "SELECT ib FROM InventoryBalance ib " +
           "JOIN FETCH ib.variant v " +
           "JOIN FETCH v.product p " +
           "JOIN FETCH p.brand b " +
           "JOIN FETCH p.subcategory sub " +
           "JOIN FETCH sub.category cat " +
           "WHERE (:brandId IS NULL OR b.id = :brandId) " +
           "AND (:categoryId IS NULL OR cat.id = :categoryId) " +
           "AND (:subcategoryId IS NULL OR sub.id = :subcategoryId) " +
           "AND (:isActive IS NULL OR (v.isActive = :isActive AND p.isActive = :isActive)) " +
           "AND (cast(:search as string) IS NULL OR (" +
           "  LOWER(p.name) LIKE LOWER(CONCAT('%', cast(:search as string), '%')) OR " +
           "  LOWER(v.sku) LIKE LOWER(CONCAT('%', cast(:search as string), '%')) OR " +
           "  LOWER(v.sizeLabel) LIKE LOWER(CONCAT('%', cast(:search as string), '%'))" +
           ")) " +
           "AND (:stockStatus IS NULL OR " +
           "  (:stockStatus = 'IN_STOCK' AND ib.quantityOnHand > 0) OR " +
           "  (:stockStatus = 'OUT_OF_STOCK' AND ib.quantityOnHand = 0)" +
           ")",
           countQuery = "SELECT COUNT(ib) FROM InventoryBalance ib " +
           "JOIN ib.variant v " +
           "JOIN v.product p " +
           "JOIN p.brand b " +
           "JOIN p.subcategory sub " +
           "JOIN sub.category cat " +
           "WHERE (:brandId IS NULL OR b.id = :brandId) " +
           "AND (:categoryId IS NULL OR cat.id = :categoryId) " +
           "AND (:subcategoryId IS NULL OR sub.id = :subcategoryId) " +
           "AND (:isActive IS NULL OR (v.isActive = :isActive AND p.isActive = :isActive)) " +
           "AND (cast(:search as string) IS NULL OR (" +
           "  LOWER(p.name) LIKE LOWER(CONCAT('%', cast(:search as string), '%')) OR " +
           "  LOWER(v.sku) LIKE LOWER(CONCAT('%', cast(:search as string), '%')) OR " +
           "  LOWER(v.sizeLabel) LIKE LOWER(CONCAT('%', cast(:search as string), '%'))" +
           ")) " +
           "AND (:stockStatus IS NULL OR " +
           "  (:stockStatus = 'IN_STOCK' AND ib.quantityOnHand > 0) OR " +
           "  (:stockStatus = 'OUT_OF_STOCK' AND ib.quantityOnHand = 0)" +
           ")")
    Page<InventoryBalance> searchInventory(
            @Param("brandId") Long brandId,
            @Param("categoryId") Long categoryId,
            @Param("subcategoryId") Long subcategoryId,
            @Param("isActive") Boolean isActive,
            @Param("search") String search,
            @Param("stockStatus") String stockStatus,
            Pageable pageable);
}
