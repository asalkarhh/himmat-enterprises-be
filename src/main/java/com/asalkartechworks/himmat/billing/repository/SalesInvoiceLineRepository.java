package com.asalkartechworks.himmat.billing.repository;

import com.asalkartechworks.himmat.billing.model.SalesInvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SalesInvoiceLineRepository extends JpaRepository<SalesInvoiceLine, Long> {
    List<SalesInvoiceLine> findByInvoiceId(Long invoiceId);

    @Query("SELECT sil FROM SalesInvoiceLine sil JOIN FETCH sil.invoice inv JOIN FETCH sil.variant v WHERE inv.status = 'POSTED' AND inv.invoiceDate >= :startDate AND inv.invoiceDate <= :endDate")
    List<SalesInvoiceLine> findLinesForProfitReport(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
