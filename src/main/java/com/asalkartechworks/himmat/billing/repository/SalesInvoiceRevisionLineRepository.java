package com.asalkartechworks.himmat.billing.repository;

import com.asalkartechworks.himmat.billing.model.SalesInvoiceRevisionLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalesInvoiceRevisionLineRepository extends JpaRepository<SalesInvoiceRevisionLine, Long> {

    List<SalesInvoiceRevisionLine> findByRevisionIdOrderByLineIndexAsc(Long revisionId);

    List<SalesInvoiceRevisionLine> findByInvoiceLineId(Long invoiceLineId);

    @Query("SELECT rl FROM SalesInvoiceRevisionLine rl WHERE rl.invoiceLine.id = :invoiceLineId ORDER BY rl.revision.revisionNumber ASC")
    List<SalesInvoiceRevisionLine> findByInvoiceLineIdOrderByRevisionAsc(@Param("invoiceLineId") Long invoiceLineId);
}
