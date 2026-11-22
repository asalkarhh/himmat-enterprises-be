package com.asalkartechworks.himmat.billing.repository;

import com.asalkartechworks.himmat.billing.model.CustomerRefundComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerRefundComponentRepository extends JpaRepository<CustomerRefundComponent, Long> {

    List<CustomerRefundComponent> findByCustomerRefundId(Long customerRefundId);
}
