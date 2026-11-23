package com.asalkartechworks.himmat.purchase.repository;

import com.asalkartechworks.himmat.purchase.model.SupplierPayment;
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
public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {

    Optional<SupplierPayment> findByPaymentNumber(String paymentNumber);

    @Query(value = "SELECT nextval('seq_supplier_payment_number')", nativeQuery = true)
    Long getNextPaymentSequence();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT sp FROM SupplierPayment sp WHERE sp.id = :id")
    Optional<SupplierPayment> findByIdWithLock(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT sp FROM SupplierPayment sp WHERE sp.supplier.id = :supplierId AND sp.unallocatedAmount > 0 ORDER BY sp.paymentDate ASC, sp.id ASC")
    List<SupplierPayment> findUnallocatedPaymentsBySupplierWithLock(@Param("supplierId") Long supplierId);

    @Query("SELECT sp FROM SupplierPayment sp WHERE sp.supplier.id = :supplierId AND sp.unallocatedAmount > 0 ORDER BY sp.paymentDate ASC, sp.id ASC")
    List<SupplierPayment> findUnallocatedPaymentsBySupplier(@Param("supplierId") Long supplierId);

    @Query("SELECT sp FROM SupplierPayment sp WHERE " +
           "sp.supplier.id = :supplierId AND " +
           "(cast(:startDate as date) IS NULL OR sp.paymentDate >= :startDate) AND " +
           "(cast(:endDate as date) IS NULL OR sp.paymentDate <= :endDate)")
    Page<SupplierPayment> searchPayments(@Param("supplierId") Long supplierId,
                                        @Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate,
                                        Pageable pageable);

    List<SupplierPayment> findByPaymentDateBetweenOrderByPaymentDateAsc(LocalDate startDate, LocalDate endDate);
}
