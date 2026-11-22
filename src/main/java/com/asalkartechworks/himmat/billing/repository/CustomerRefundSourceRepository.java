package com.asalkartechworks.himmat.billing.repository;

import com.asalkartechworks.himmat.billing.model.CustomerRefundSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerRefundSourceRepository extends JpaRepository<CustomerRefundSource, Long> {

    List<CustomerRefundSource> findByCustomerRefundId(Long customerRefundId);
}
