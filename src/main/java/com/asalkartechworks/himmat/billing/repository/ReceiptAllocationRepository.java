package com.asalkartechworks.himmat.billing.repository;

import com.asalkartechworks.himmat.billing.model.ReceiptAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReceiptAllocationRepository extends JpaRepository<ReceiptAllocation, Long> {

    List<ReceiptAllocation> findByCustomerReceiptId(Long customerReceiptId);

    List<ReceiptAllocation> findByInvoiceId(Long invoiceId);

    @Query("SELECT ra FROM ReceiptAllocation ra WHERE ra.invoice.id = :invoiceId ORDER BY ra.id DESC")
    List<ReceiptAllocation> findByInvoiceIdOrderByIdDesc(@Param("invoiceId") Long invoiceId);
}
