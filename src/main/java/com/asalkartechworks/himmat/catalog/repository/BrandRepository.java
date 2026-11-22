package com.asalkartechworks.himmat.catalog.repository;

import com.asalkartechworks.himmat.catalog.model.Brand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BrandRepository extends JpaRepository<Brand, Long> {

    @Query("SELECT b FROM Brand b WHERE LOWER(TRIM(b.name)) = LOWER(TRIM(:name))")
    Optional<Brand> findByNameIgnoreCase(@Param("name") String name);

    @Query("SELECT COUNT(b) > 0 FROM Brand b WHERE LOWER(TRIM(b.name)) = LOWER(TRIM(:name))")
    boolean existsByNameIgnoreCase(@Param("name") String name);

    @Query("SELECT COUNT(b) > 0 FROM Brand b WHERE LOWER(TRIM(b.name)) = LOWER(TRIM(:name)) AND b.id != :id")
    boolean existsByNameIgnoreCaseAndIdNot(@Param("name") String name, @Param("id") Long id);

    @Query("SELECT b FROM Brand b WHERE " +
           "(:isActive IS NULL OR b.isActive = :isActive) AND " +
           "(:search IS NULL OR LOWER(b.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Brand> searchBrands(@Param("search") String search,
                             @Param("isActive") Boolean isActive,
                             Pageable pageable);
}
