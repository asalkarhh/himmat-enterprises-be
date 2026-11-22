package com.asalkartechworks.himmat.catalog.repository;

import com.asalkartechworks.himmat.catalog.model.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("SELECT c FROM Category c WHERE LOWER(TRIM(c.name)) = LOWER(TRIM(:name))")
    Optional<Category> findByNameIgnoreCase(@Param("name") String name);

    @Query("SELECT COUNT(c) > 0 FROM Category c WHERE LOWER(TRIM(c.name)) = LOWER(TRIM(:name))")
    boolean existsByNameIgnoreCase(@Param("name") String name);

    @Query("SELECT COUNT(c) > 0 FROM Category c WHERE LOWER(TRIM(c.name)) = LOWER(TRIM(:name)) AND c.id != :id")
    boolean existsByNameIgnoreCaseAndIdNot(@Param("name") String name, @Param("id") Long id);

    @Query("SELECT c FROM Category c WHERE " +
           "(:isActive IS NULL OR c.isActive = :isActive) AND " +
           "(:search IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Category> searchCategories(@Param("search") String search,
                                   @Param("isActive") Boolean isActive,
                                   Pageable pageable);
}
