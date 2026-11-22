package com.asalkartechworks.himmat.purchase.repository;

import com.asalkartechworks.himmat.purchase.model.PurchaseLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseLineRepository extends JpaRepository<PurchaseLine, Long> {
    List<PurchaseLine> findByPurchaseId(Long purchaseId);
}
