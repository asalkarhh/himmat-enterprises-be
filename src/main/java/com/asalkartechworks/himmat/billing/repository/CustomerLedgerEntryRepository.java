package com.asalkartechworks.himmat.billing.repository;

import com.asalkartechworks.himmat.billing.model.CustomerLedgerEntry;
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
public interface CustomerLedgerEntryRepository extends JpaRepository<CustomerLedgerEntry, Long> {

    Optional<CustomerLedgerEntry> findFirstByCustomerIdOrderByIdDesc(Long customerId);

    @Query("SELECT cle FROM CustomerLedgerEntry cle WHERE " +
           "cle.customer.id = :customerId AND " +
           "cle.effectiveDate >= :startDate AND " +
           "cle.effectiveDate <= :endDate " +
           "ORDER BY cle.effectiveDate ASC, cle.id ASC")
    Page<CustomerLedgerEntry> findByCustomerIdAndDateRange(@Param("customerId") Long customerId,
                                                           @Param("startDate") LocalDate startDate,
                                                           @Param("endDate") LocalDate endDate,
                                                           Pageable pageable);

    @Query("SELECT COALESCE(SUM(cle.amount), 0.0) FROM CustomerLedgerEntry cle " +
           "WHERE cle.customer.id = :customerId AND cle.effectiveDate < :startDate")
    BigDecimal calculateOpeningBalance(@Param("customerId") Long customerId,
                                      @Param("startDate") LocalDate startDate);

    @Query("SELECT COALESCE(SUM(cle.amount), 0.0) FROM CustomerLedgerEntry cle " +
           "WHERE cle.customer.id = :customerId AND cle.sourceType = 'INVOICE'")
    BigDecimal calculateTotalInvoiced(@Param("customerId") Long customerId);

    @Query("SELECT COALESCE(SUM(cle.amount), 0.0) FROM CustomerLedgerEntry cle " +
           "WHERE cle.customer.id = :customerId AND cle.sourceType = 'RECEIPT'")
    BigDecimal calculateTotalReceipts(@Param("customerId") Long customerId);

    @Query("SELECT COALESCE(SUM(cle.amount), 0.0) FROM CustomerLedgerEntry cle " +
           "WHERE cle.customer.id = :customerId AND cle.sourceType = 'CUSTOMER_RETURN'")
    BigDecimal calculateTotalReturns(@Param("customerId") Long customerId);

    @Query("SELECT COALESCE(SUM(cle.amount), 0.0) FROM CustomerLedgerEntry cle " +
           "WHERE cle.customer.id = :customerId AND cle.sourceType = 'REFUND'")
    BigDecimal calculateTotalRefunds(@Param("customerId") Long customerId);
}
