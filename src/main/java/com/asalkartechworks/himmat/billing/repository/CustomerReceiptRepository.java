package com.asalkartechworks.himmat.billing.repository;

import com.asalkartechworks.himmat.billing.model.CustomerReceipt;
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
public interface CustomerReceiptRepository extends JpaRepository<CustomerReceipt, Long> {

    Optional<CustomerReceipt> findByReceiptNumber(String receiptNumber);

    @Query(value = "SELECT nextval('seq_customer_receipt_number')", nativeQuery = true)
    Long getNextReceiptSequence();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cr FROM CustomerReceipt cr WHERE cr.id = :id")
    Optional<CustomerReceipt> findByIdWithLock(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cr FROM CustomerReceipt cr WHERE cr.customer.id = :customerId AND cr.unallocatedAmount > 0 ORDER BY cr.receiptDate ASC, cr.id ASC")
    List<CustomerReceipt> findUnallocatedReceiptsByCustomerWithLock(@Param("customerId") Long customerId);

    @Query("SELECT cr FROM CustomerReceipt cr WHERE cr.customer.id = :customerId AND cr.unallocatedAmount > 0 ORDER BY cr.receiptDate ASC, cr.id ASC")
    List<CustomerReceipt> findUnallocatedReceiptsByCustomer(@Param("customerId") Long customerId);

    @Query("SELECT cr FROM CustomerReceipt cr WHERE " +
           "cr.customer.id = :customerId AND " +
           "(cast(:startDate as date) IS NULL OR cr.receiptDate >= :startDate) AND " +
           "(cast(:endDate as date) IS NULL OR cr.receiptDate <= :endDate)")
    Page<CustomerReceipt> searchReceipts(@Param("customerId") Long customerId,
                                         @Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate,
                                         Pageable pageable);
}
