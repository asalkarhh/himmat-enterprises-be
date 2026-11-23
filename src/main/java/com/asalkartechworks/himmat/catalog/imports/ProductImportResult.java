package com.asalkartechworks.himmat.catalog.imports;

import com.asalkartechworks.himmat.catalog.model.ImportMode;
import com.asalkartechworks.himmat.catalog.model.ImportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImportResult {

    private String runCode;
    private String sourceFilename;
    private String sourceChecksum;
    private ImportMode mode;
    private ImportStatus status;
    private String operatorIdentity;

    @Builder.Default
    private int totalRows = 0;

    @Builder.Default
    private int createdRows = 0;

    @Builder.Default
    private int updatedRows = 0;

    @Builder.Default
    private int unchangedRows = 0;

    @Builder.Default
    private int rejectedRows = 0;

    private Instant startedAt;
    private Instant completedAt;
    private long durationMs;
    private String errorSummary;

    @Builder.Default
    private List<ProductImportRowOutcome> rowOutcomes = new ArrayList<>();

    public String humanReadableSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("====================================================\n");
        sb.append("             PRODUCT IMPORT SUMMARY\n");
        sb.append("====================================================\n");
        sb.append(String.format(" Run Code      : %s\n", runCode));
        sb.append(String.format(" File          : %s\n", sourceFilename));
        sb.append(String.format(" Checksum      : %s\n", sourceChecksum));
        sb.append(String.format(" Mode          : %s\n", mode));
        sb.append(String.format(" Status        : %s\n", status));
        sb.append(String.format(" Operator      : %s\n", operatorIdentity));
        sb.append(String.format(" Duration      : %d ms\n", durationMs));
        sb.append("----------------------------------------------------\n");
        sb.append(String.format(" Total Rows    : %d\n", totalRows));
        sb.append(String.format(" Created Rows  : %d\n", createdRows));
        sb.append(String.format(" Updated Rows  : %d\n", updatedRows));
        sb.append(String.format(" Unchanged Rows: %d\n", unchangedRows));
        sb.append(String.format(" Rejected Rows : %d\n", rejectedRows));
        sb.append("====================================================\n");
        if (errorSummary != null && !errorSummary.isBlank()) {
            sb.append(" Errors / Conflicts:\n");
            sb.append(errorSummary).append("\n");
            sb.append("====================================================\n");
        }
        return sb.toString();
    }
}
