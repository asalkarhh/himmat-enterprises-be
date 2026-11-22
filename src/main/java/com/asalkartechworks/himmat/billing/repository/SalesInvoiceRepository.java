package com.asalkartechworks.himmat.billing.repository;

import com.asalkartechworks.himmat.billing.model.InvoiceDisplayFormat;
import com.asalkartechworks.himmat.billing.model.InvoiceStatus;
import com.asalkartechworks.himmat.billing.model.SalesInvoice;
import com.asalkartechworks.himmat.billing.model.SettlementStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SalesInvoiceRepository extends JpaRepository<SalesInvoice, Long> {

    Optional<SalesInvoice> findByInvoiceNumber(String invoiceNumber);

    @Query(value = "SELECT nextval('seq_invoice_number')", nativeQuery = true)
    Long getNextInvoiceSequence();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT si FROM SalesInvoice si WHERE si.id = :id")
    Optional<SalesInvoice> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT si FROM SalesInvoice si WHERE si.customer.id = :customerId AND si.status = 'POSTED' AND (si.totalPayableAmount - si.totalReturnedAmount) > si.totalAllocatedAmount ORDER BY si.invoiceDate ASC, si.id ASC")
    List<SalesInvoice> findUnpaidInvoicesByCustomer(@Param("customerId") Long customerId);

    @Query("SELECT si FROM SalesInvoice si WHERE " +
           "(:customerId IS NULL OR si.customer.id = :customerId) AND " +
           "(:siteId IS NULL OR (si.customerSite IS NOT NULL AND si.customerSite.id = :siteId)) AND " +
           "(:status IS NULL OR si.status = :status) AND " +
           "(:settlementStatus IS NULL OR si.settlementStatus = :settlementStatus) AND " +
           "(:displayFormat IS NULL OR si.displayFormat = :displayFormat) AND " +
           "(cast(:startDate as date) IS NULL OR si.invoiceDate >= :startDate) AND " +
           "(cast(:endDate as date) IS NULL OR si.invoiceDate <= :endDate) AND " +
           "(:overdueOnly IS NULL OR :overdueOnly = FALSE OR (si.status = 'POSTED' AND (si.totalPayableAmount - si.totalReturnedAmount) > si.totalAllocatedAmount AND si.dueDate < :currentDate)) AND " +
           "(cast(:search as string) IS NULL OR " +
           "LOWER(si.invoiceNumber) LIKE LOWER(CONCAT('%', cast(:search as string), '%')) OR " +
           "LOWER(si.customerName) LIKE LOWER(CONCAT('%', cast(:search as string), '%')))")
    Page<SalesInvoice> searchInvoices(@Param("customerId") Long customerId,
                                      @Param("siteId") Long siteId,
                                      @Param("status") InvoiceStatus status,
                                      @Param("settlementStatus") SettlementStatus settlementStatus,
                                      @Param("displayFormat") InvoiceDisplayFormat displayFormat,
                                      @Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate,
                                      @Param("overdueOnly") Boolean overdueOnly,
                                      @Param("currentDate") LocalDate currentDate,
                                      @Param("search") String search,
                                      Pageable pageable);

    @Query("SELECT si FROM SalesInvoice si WHERE " +
           "si.status = 'POSTED' AND (si.totalPayableAmount - si.totalReturnedAmount) > si.totalAllocatedAmount AND " +
           "(:customerId IS NULL OR si.customer.id = :customerId) AND " +
           "(:siteId IS NULL OR (si.customerSite IS NOT NULL AND si.customerSite.id = :siteId)) AND " +
           "(cast(:dueStartDate as date) IS NULL OR si.dueDate >= :dueStartDate) AND " +
           "(cast(:dueEndDate as date) IS NULL OR si.dueDate <= :dueEndDate) AND " +
           "(:overdueOnly IS NULL OR :overdueOnly = FALSE OR si.dueDate < :currentDate)")
    Page<SalesInvoice> searchDueInvoices(@Param("customerId") Long customerId,
                                         @Param("siteId") Long siteId,
                                         @Param("dueStartDate") LocalDate dueStartDate,
                                         @Param("dueEndDate") LocalDate dueEndDate,
                                         @Param("overdueOnly") Boolean overdueOnly,
                                         @Param("currentDate") LocalDate currentDate,
                                         Pageable pageable);

    @Query("SELECT si FROM SalesInvoice si WHERE " +
           "si.status = 'POSTED' AND (si.totalPayableAmount - si.totalReturnedAmount) > si.totalAllocatedAmount AND " +
           "(:customerId IS NULL OR si.customer.id = :customerId) AND " +
           "(:siteId IS NULL OR (si.customerSite IS NOT NULL AND si.customerSite.id = :siteId)) AND " +
           "(cast(:dueStartDate as date) IS NULL OR si.dueDate >= :dueStartDate) AND " +
           "(cast(:dueEndDate as date) IS NULL OR si.dueDate <= :dueEndDate) AND " +
           "(:overdueOnly IS NULL OR :overdueOnly = FALSE OR si.dueDate < :currentDate)")
    List<SalesInvoice> findAllDueInvoicesForSummary(@Param("customerId") Long customerId,
                                                   @Param("siteId") Long siteId,
                                                   @Param("dueStartDate") LocalDate dueStartDate,
                                                   @Param("dueEndDate") LocalDate dueEndDate,
                                                   @Param("overdueOnly") Boolean overdueOnly,
                                                   @Param("currentDate") LocalDate currentDate);
}
