package com.asalkartechworks.himmat.billing.repository;

import com.asalkartechworks.himmat.billing.model.CustomerReturn;
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
public interface CustomerReturnRepository extends JpaRepository<CustomerReturn, Long> {

    Optional<CustomerReturn> findByReturnNumber(String returnNumber);

    List<CustomerReturn> findByInvoiceIdOrderByReturnDateAsc(Long invoiceId);

    @Query(value = "SELECT nextval('seq_customer_return_number')", nativeQuery = true)
    Long getNextReturnSequence();

    @Query("SELECT cr FROM CustomerReturn cr WHERE " +
           "(:customerId IS NULL OR cr.customer.id = :customerId) AND " +
           "(:invoiceId IS NULL OR cr.invoice.id = :invoiceId) AND " +
           "(cast(:startDate as date) IS NULL OR cr.returnDate >= :startDate) AND " +
           "(cast(:endDate as date) IS NULL OR cr.returnDate <= :endDate) " +
           "ORDER BY cr.returnDate DESC, cr.id DESC")
    Page<CustomerReturn> searchReturns(@Param("customerId") Long customerId,
                                      @Param("invoiceId") Long invoiceId,
                                      @Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate,
                                      Pageable pageable);
}
