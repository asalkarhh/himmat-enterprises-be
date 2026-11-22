package com.asalkartechworks.himmat.party.repository;

import com.asalkartechworks.himmat.party.model.CustomerSite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerSiteRepository extends JpaRepository<CustomerSite, Long> {

    @Query("SELECT s FROM CustomerSite s JOIN FETCH s.customer WHERE s.id = :id AND s.customer.id = :customerId")
    Optional<CustomerSite> findByIdAndCustomerId(@Param("id") Long id, @Param("customerId") Long customerId);

    @Query("SELECT s FROM CustomerSite s JOIN FETCH s.customer WHERE s.id = :id")
    Optional<CustomerSite> findByIdWithCustomer(@Param("id") Long id);

    @Query("SELECT COUNT(s) > 0 FROM CustomerSite s WHERE s.customer.id = :customerId AND s.normalizedSiteName = :normalizedSiteName")
    boolean existsByCustomerIdAndNormalizedSiteName(@Param("customerId") Long customerId,
                                                    @Param("normalizedSiteName") String normalizedSiteName);

    @Query("SELECT COUNT(s) > 0 FROM CustomerSite s WHERE s.customer.id = :customerId AND s.normalizedSiteName = :normalizedSiteName AND s.id != :id")
    boolean existsByCustomerIdAndNormalizedSiteNameAndIdNot(@Param("customerId") Long customerId,
                                                            @Param("normalizedSiteName") String normalizedSiteName,
                                                            @Param("id") Long id);

    @Query("SELECT s FROM CustomerSite s WHERE " +
           "s.customer.id = :customerId AND " +
           "(:isActive IS NULL OR s.isActive = :isActive) AND " +
           "(:search IS NULL OR LOWER(s.siteName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<CustomerSite> searchSites(@Param("customerId") Long customerId,
                                   @Param("search") String search,
                                   @Param("isActive") Boolean isActive,
                                   Pageable pageable);
}
