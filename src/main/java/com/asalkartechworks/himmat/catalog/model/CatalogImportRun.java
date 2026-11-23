package com.asalkartechworks.himmat.catalog.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "catalog_import_run")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogImportRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_code", nullable = false, unique = true, length = 50)
    private String runCode;

    @Column(name = "source_filename", nullable = false)
    private String sourceFilename;

    @Column(name = "source_file_checksum", nullable = false, length = 64)
    private String sourceFileChecksum;

    @Column(name = "mapping_version", nullable = false, length = 50)
    private String mappingVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "import_mode", nullable = false, length = 30)
    private ImportMode importMode;

    @Column(name = "operator_identity", nullable = false, length = 100)
    private String operatorIdentity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ImportStatus status;

    @Builder.Default
    @Column(name = "total_rows", nullable = false)
    private Integer totalRows = 0;

    @Builder.Default
    @Column(name = "created_rows", nullable = false)
    private Integer createdRows = 0;

    @Builder.Default
    @Column(name = "updated_rows", nullable = false)
    private Integer updatedRows = 0;

    @Builder.Default
    @Column(name = "unchanged_rows", nullable = false)
    private Integer unchangedRows = 0;

    @Builder.Default
    @Column(name = "rejected_rows", nullable = false)
    private Integer rejectedRows = 0;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_summary", columnDefinition = "TEXT")
    private String errorSummary;

    @Builder.Default
    @OneToMany(mappedBy = "importRun", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CatalogImportRow> rows = new ArrayList<>();
}
