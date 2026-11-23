package com.asalkartechworks.himmat.billing.repository;

import com.asalkartechworks.himmat.billing.model.CustomerRefund;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRefundRepository extends JpaRepository<CustomerRefund, Long> {

    Optional<CustomerRefund> findByRefundNumber(String refundNumber);

    @Query(value = "SELECT nextval('seq_customer_refund_number')", nativeQuery = true)
    Long getNextRefundSequence();

    @Query("SELECT cr FROM CustomerRefund cr WHERE " +
           "(:customerId IS NULL OR cr.customer.id = :customerId) AND " +
           "(cast(:startDate as date) IS NULL OR cr.refundDate >= :startDate) AND " +
           "(cast(:endDate as date) IS NULL OR cr.refundDate <= :endDate) " +
           "ORDER BY cr.refundDate DESC, cr.id DESC")
    Page<CustomerRefund> searchRefunds(@Param("customerId") Long customerId,
                                      @Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate,
                                      Pageable pageable);

    List<CustomerRefund> findByRefundDateBetweenOrderByRefundDateAsc(LocalDate startDate, LocalDate endDate);
}
