package com.asalkartechworks.himmat.catalog.repository;

import com.asalkartechworks.himmat.catalog.model.CatalogImportRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CatalogImportRowRepository extends JpaRepository<CatalogImportRow, Long> {

    List<CatalogImportRow> findByImportRunIdOrderByRowNumberAsc(Long importRunId);
}
