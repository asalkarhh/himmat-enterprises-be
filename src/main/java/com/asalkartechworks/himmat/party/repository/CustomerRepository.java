package com.asalkartechworks.himmat.party.repository;

import com.asalkartechworks.himmat.party.model.Customer;
import com.asalkartechworks.himmat.party.model.CustomerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByCustomerCode(String customerCode);

    boolean existsByCustomerCode(String customerCode);

    @Query(value = "SELECT nextval('seq_customer_code')", nativeQuery = true)
    Long getNextCustomerSequence();

    @Query("SELECT c FROM Customer c WHERE " +
           "(:customerType IS NULL OR c.customerType = :customerType) AND " +
           "(:isActive IS NULL OR c.isActive = :isActive) AND " +
           "(:search IS NULL OR " +
           "LOWER(c.customerCode) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "(c.phone IS NOT NULL AND c.phone LIKE CONCAT('%', :search, '%')))")
    Page<Customer> searchCustomers(@Param("search") String search,
                                  @Param("customerType") CustomerType customerType,
                                  @Param("isActive") Boolean isActive,
                                  Pageable pageable);
}
