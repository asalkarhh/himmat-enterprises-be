package com.asalkartechworks.himmat.catalog.imports;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImportRow {

    private int rowNumber;
    private String sheetName;
    private String srNo;
    private String category;
    private String subcategory;
    private String productName;
    private String sizeLabel;
    private BigDecimal price;
    private String sourceIdentity;
    private Map<String, String> rawValues;
}
