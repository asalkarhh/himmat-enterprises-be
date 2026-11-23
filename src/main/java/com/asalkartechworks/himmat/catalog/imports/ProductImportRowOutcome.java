package com.asalkartechworks.himmat.catalog.imports;

import com.asalkartechworks.himmat.catalog.model.ImportRowAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImportRowOutcome {

    private int rowNumber;
    private String sheetName;
    private String sourceIdentity;
    private ImportRowAction action;
    private Long variantId;
    private String sku;
    private Long previousVersion;
    private Long resultingVersion;
    private String errorMessage;
}
