package com.asalkartechworks.himmat.purchase.repository;

import com.asalkartechworks.himmat.purchase.model.PurchaseReturnLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseReturnLineRepository extends JpaRepository<PurchaseReturnLine, Long> {
    List<PurchaseReturnLine> findByPurchaseReturnId(Long purchaseReturnId);
    List<PurchaseReturnLine> findByPurchaseLineId(Long purchaseLineId);
}
