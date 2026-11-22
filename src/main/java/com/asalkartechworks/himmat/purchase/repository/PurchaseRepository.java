package com.asalkartechworks.himmat.purchase.repository;

import com.asalkartechworks.himmat.purchase.model.Purchase;
import com.asalkartechworks.himmat.purchase.model.PurchaseStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    Optional<Purchase> findByPurchaseNumber(String purchaseNumber);

    @Query(value = "SELECT nextval('seq_purchase_number')", nativeQuery = true)
    Long getNextPurchaseSequence();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Purchase p WHERE p.id = :id")
    Optional<Purchase> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT p FROM Purchase p WHERE p.supplier.id = :supplierId AND p.status = 'POSTED' AND p.totalPayableAmount > p.totalAllocatedAmount ORDER BY p.purchaseDate ASC, p.id ASC")
    List<Purchase> findUnpaidPurchasesBySupplier(@Param("supplierId") Long supplierId);

    @Query("SELECT p FROM Purchase p WHERE " +
           "(:supplierId IS NULL OR p.supplier.id = :supplierId) AND " +
           "(:status IS NULL OR p.status = :status) AND " +
           "(cast(:startDate as date) IS NULL OR p.purchaseDate >= :startDate) AND " +
           "(cast(:endDate as date) IS NULL OR p.purchaseDate <= :endDate) AND " +
           "(cast(:search as string) IS NULL OR " +
           "LOWER(p.purchaseNumber) LIKE LOWER(CONCAT('%', cast(:search as string), '%')) OR " +
           "(p.supplierInvoiceNumber IS NOT NULL AND LOWER(p.supplierInvoiceNumber) LIKE LOWER(CONCAT('%', cast(:search as string), '%'))))")
    Page<Purchase> searchPurchases(@Param("supplierId") Long supplierId,
                                  @Param("status") PurchaseStatus status,
                                  @Param("startDate") LocalDate startDate,
                                  @Param("endDate") LocalDate endDate,
                                  @Param("search") String search,
                                  Pageable pageable);
}
