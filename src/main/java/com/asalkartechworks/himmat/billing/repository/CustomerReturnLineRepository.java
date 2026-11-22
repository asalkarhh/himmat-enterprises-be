package com.asalkartechworks.himmat.billing.repository;

import com.asalkartechworks.himmat.billing.model.CustomerReturnLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerReturnLineRepository extends JpaRepository<CustomerReturnLine, Long> {

    List<CustomerReturnLine> findByCustomerReturnId(Long customerReturnId);

    List<CustomerReturnLine> findByInvoiceLineId(Long invoiceLineId);
}
