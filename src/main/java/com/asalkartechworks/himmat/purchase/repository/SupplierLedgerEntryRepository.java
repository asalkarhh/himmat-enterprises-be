package com.asalkartechworks.himmat.purchase.repository;

import com.asalkartechworks.himmat.purchase.model.SupplierLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface SupplierLedgerEntryRepository extends JpaRepository<SupplierLedgerEntry, Long> {

    Optional<SupplierLedgerEntry> findFirstBySupplierIdOrderByIdDesc(Long supplierId);

    @Query("SELECT sle FROM SupplierLedgerEntry sle WHERE " +
           "sle.supplier.id = :supplierId AND " +
           "sle.effectiveDate >= :startDate AND " +
           "sle.effectiveDate <= :endDate " +
           "ORDER BY sle.effectiveDate ASC, sle.id ASC")
    Page<SupplierLedgerEntry> findBySupplierIdAndDateRange(@Param("supplierId") Long supplierId,
                                                           @Param("startDate") LocalDate startDate,
                                                           @Param("endDate") LocalDate endDate,
                                                           Pageable pageable);

    @Query("SELECT COALESCE(SUM(sle.amount), 0.0) FROM SupplierLedgerEntry sle " +
           "WHERE sle.supplier.id = :supplierId AND sle.effectiveDate < :startDate")
    BigDecimal calculateOpeningBalance(@Param("supplierId") Long supplierId,
                                      @Param("startDate") LocalDate startDate);

    @Query("SELECT COALESCE(SUM(sle.amount), 0.0) FROM SupplierLedgerEntry sle " +
           "WHERE sle.supplier.id = :supplierId AND sle.sourceType = 'PURCHASE'")
    BigDecimal calculateTotalPurchases(@Param("supplierId") Long supplierId);

    @Query("SELECT COALESCE(SUM(sle.amount), 0.0) FROM SupplierLedgerEntry sle " +
           "WHERE sle.supplier.id = :supplierId AND sle.sourceType = 'PAYMENT'")
    BigDecimal calculateTotalPayments(@Param("supplierId") Long supplierId);

    @Query("SELECT COALESCE(SUM(sle.amount), 0.0) FROM SupplierLedgerEntry sle " +
           "WHERE sle.supplier.id = :supplierId AND sle.sourceType = 'PURCHASE_RETURN'")
    BigDecimal calculateTotalReturns(@Param("supplierId") Long supplierId);

    @Query("SELECT COALESCE(SUM(sle.amount), 0.0) FROM SupplierLedgerEntry sle " +
           "WHERE sle.supplier.id = :supplierId AND sle.sourceType = 'REFUND'")
    BigDecimal calculateTotalRefunds(@Param("supplierId") Long supplierId);
}
