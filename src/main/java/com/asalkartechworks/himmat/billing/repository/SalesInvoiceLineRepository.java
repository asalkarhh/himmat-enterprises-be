package com.asalkartechworks.himmat.billing.repository;

import com.asalkartechworks.himmat.billing.model.SalesInvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalesInvoiceLineRepository extends JpaRepository<SalesInvoiceLine, Long> {
    List<SalesInvoiceLine> findByInvoiceId(Long invoiceId);
}
