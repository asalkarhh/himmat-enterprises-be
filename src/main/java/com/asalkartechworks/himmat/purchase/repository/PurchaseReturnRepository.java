package com.asalkartechworks.himmat.purchase.repository;

import com.asalkartechworks.himmat.purchase.model.PurchaseReturn;
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
public interface PurchaseReturnRepository extends JpaRepository<PurchaseReturn, Long> {

    Optional<PurchaseReturn> findByReturnNumber(String returnNumber);

    @Query(value = "SELECT nextval('seq_purchase_return_number')", nativeQuery = true)
    Long getNextReturnSequence();

    List<PurchaseReturn> findByPurchaseId(Long purchaseId);

    @Query("SELECT pr FROM PurchaseReturn pr WHERE " +
           "(:supplierId IS NULL OR pr.supplier.id = :supplierId) AND " +
           "(:purchaseId IS NULL OR pr.purchase.id = :purchaseId) AND " +
           "(cast(:startDate as date) IS NULL OR pr.returnDate >= :startDate) AND " +
           "(cast(:endDate as date) IS NULL OR pr.returnDate <= :endDate)")
    Page<PurchaseReturn> searchReturns(@Param("supplierId") Long supplierId,
                                      @Param("purchaseId") Long purchaseId,
                                      @Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate,
                                      Pageable pageable);
}
