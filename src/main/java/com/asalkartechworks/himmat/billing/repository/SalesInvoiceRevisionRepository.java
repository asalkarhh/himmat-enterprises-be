package com.asalkartechworks.himmat.billing.repository;

import com.asalkartechworks.himmat.billing.model.SalesInvoiceRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SalesInvoiceRevisionRepository extends JpaRepository<SalesInvoiceRevision, Long> {

    List<SalesInvoiceRevision> findByInvoiceIdOrderByRevisionNumberAsc(Long invoiceId);

    Optional<SalesInvoiceRevision> findByInvoiceIdAndRevisionNumber(Long invoiceId, Integer revisionNumber);

    @Query("SELECT MAX(r.revisionNumber) FROM SalesInvoiceRevision r WHERE r.invoice.id = :invoiceId")
    Optional<Integer> findMaxRevisionNumberByInvoiceId(@Param("invoiceId") Long invoiceId);
}
