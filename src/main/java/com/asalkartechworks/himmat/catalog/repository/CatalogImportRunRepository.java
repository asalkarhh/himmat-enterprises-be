package com.asalkartechworks.himmat.catalog.repository;

import com.asalkartechworks.himmat.catalog.model.CatalogImportRun;
import com.asalkartechworks.himmat.catalog.model.ImportMode;
import com.asalkartechworks.himmat.catalog.model.ImportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CatalogImportRunRepository extends JpaRepository<CatalogImportRun, Long> {

    Optional<CatalogImportRun> findByRunCode(String runCode);

    List<CatalogImportRun> findBySourceFileChecksumAndImportModeAndStatus(
            String sourceFileChecksum, ImportMode importMode, ImportStatus status
    );

    @Query(value = "SELECT nextval('seq_import_run_number')", nativeQuery = true)
    Long getNextRunSequenceNumber();
}
