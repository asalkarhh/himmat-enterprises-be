package com.asalkartechworks.himmat.catalog.imports;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImportConfig {

    @Builder.Default
    private String sheetName = "Price List";

    @Builder.Default
    private int headerRowNumber = 1; // 1-indexed

    @Builder.Default
    private String brandName = "Finolex";

    @Builder.Default
    private String srNoCol = "A";

    @Builder.Default
    private String categoryCol = "B";

    @Builder.Default
    private String subcategoryCol = "C";

    @Builder.Default
    private String productNameCol = "D";

    @Builder.Default
    private String sizeLabelCol = "E";

    @Builder.Default
    private String priceCol = "F";

    @Builder.Default
    private String skuPrefix = "FIN-";

    @Builder.Default
    private String mappingVersion = "v1.0-finolex";

    @Builder.Default
    private int maxRows = 25000;

    public static ProductImportConfig defaultFinolexConfig() {
        return ProductImportConfig.builder().build();
    }
}
