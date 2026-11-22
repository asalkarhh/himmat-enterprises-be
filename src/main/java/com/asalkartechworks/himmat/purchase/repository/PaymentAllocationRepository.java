package com.asalkartechworks.himmat.purchase.repository;

import com.asalkartechworks.himmat.purchase.model.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {

    List<PaymentAllocation> findBySupplierPaymentId(Long supplierPaymentId);

    List<PaymentAllocation> findByPurchaseId(Long purchaseId);

    @Query("SELECT pa FROM PaymentAllocation pa WHERE pa.purchase.id = :purchaseId ORDER BY pa.id DESC")
    List<PaymentAllocation> findByPurchaseIdOrderByIdDesc(@Param("purchaseId") Long purchaseId);
}
