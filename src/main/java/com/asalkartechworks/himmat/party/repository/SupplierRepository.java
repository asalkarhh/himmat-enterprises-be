package com.asalkartechworks.himmat.party.repository;

import com.asalkartechworks.himmat.party.model.Supplier;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findBySupplierCode(String supplierCode);

    boolean existsBySupplierCode(String supplierCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Supplier s WHERE s.id = :id")
    Optional<Supplier> findByIdWithLock(@Param("id") Long id);

    @Query(value = "SELECT nextval('seq_supplier_code')", nativeQuery = true)
    Long getNextSupplierSequence();

    @Query("SELECT s FROM Supplier s WHERE " +
           "(:isActive IS NULL OR s.isActive = :isActive) AND " +
           "(:search IS NULL OR " +
           "LOWER(s.supplierCode) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "(s.phone IS NOT NULL AND s.phone LIKE CONCAT('%', :search, '%')))")
    Page<Supplier> searchSuppliers(@Param("search") String search,
                                  @Param("isActive") Boolean isActive,
                                  Pageable pageable);
}
