package com.asalkartechworks.himmat.purchase.repository;

import com.asalkartechworks.himmat.purchase.model.SupplierRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierRefundRepository extends JpaRepository<SupplierRefund, Long> {

    Optional<SupplierRefund> findByRefundNumber(String refundNumber);

    @Query(value = "SELECT nextval('seq_supplier_refund_number')", nativeQuery = true)
    Long getNextRefundSequence();

    List<SupplierRefund> findByRefundDateBetweenOrderByRefundDateAsc(LocalDate startDate, LocalDate endDate);
}
