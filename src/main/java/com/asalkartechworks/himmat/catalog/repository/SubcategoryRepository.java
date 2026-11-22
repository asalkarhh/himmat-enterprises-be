package com.asalkartechworks.himmat.catalog.repository;

import com.asalkartechworks.himmat.catalog.model.Subcategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubcategoryRepository extends JpaRepository<Subcategory, Long> {

    @Query("SELECT s FROM Subcategory s WHERE s.category.id = :categoryId AND LOWER(TRIM(s.name)) = LOWER(TRIM(:name))")
    Optional<Subcategory> findByCategoryIdAndNameIgnoreCase(@Param("categoryId") Long categoryId, @Param("name") String name);

    @Query("SELECT COUNT(s) > 0 FROM Subcategory s WHERE s.category.id = :categoryId AND LOWER(TRIM(s.name)) = LOWER(TRIM(:name))")
    boolean existsByCategoryIdAndNameIgnoreCase(@Param("categoryId") Long categoryId, @Param("name") String name);

    @Query("SELECT COUNT(s) > 0 FROM Subcategory s WHERE s.category.id = :categoryId AND LOWER(TRIM(s.name)) = LOWER(TRIM(:name)) AND s.id != :id")
    boolean existsByCategoryIdAndNameIgnoreCaseAndIdNot(@Param("categoryId") Long categoryId, @Param("name") String name, @Param("id") Long id);

    @Query("SELECT s FROM Subcategory s WHERE " +
           "(:categoryId IS NULL OR s.category.id = :categoryId) AND " +
           "(:isActive IS NULL OR s.isActive = :isActive) AND " +
           "(:search IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Subcategory> searchSubcategories(@Param("categoryId") Long categoryId,
                                         @Param("search") String search,
                                         @Param("isActive") Boolean isActive,
                                         Pageable pageable);
}
