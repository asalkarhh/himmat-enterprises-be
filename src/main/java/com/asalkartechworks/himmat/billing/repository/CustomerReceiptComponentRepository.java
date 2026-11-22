package com.asalkartechworks.himmat.billing.repository;

import com.asalkartechworks.himmat.billing.model.CustomerReceiptComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerReceiptComponentRepository extends JpaRepository<CustomerReceiptComponent, Long> {
    List<CustomerReceiptComponent> findByCustomerReceiptId(Long customerReceiptId);
}
