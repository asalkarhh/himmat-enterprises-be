package com.asalkartechworks.himmat.catalog.imports;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ProductImportParser {

    private final DataFormatter dataFormatter = new DataFormatter();

    public static class ParseResult {
        private final List<ProductImportRow> rows = new ArrayList<>();
        private final List<String> parseErrors = new ArrayList<>();

        public List<ProductImportRow> getRows() {
            return rows;
        }

        public List<String> getParseErrors() {
            return parseErrors;
        }

        public boolean hasErrors() {
            return !parseErrors.isEmpty();
        }
    }

    public ParseResult parse(InputStream inputStream, ProductImportConfig config) {
        ParseResult result = new ParseResult();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = findSheet(workbook, config.getSheetName());
            if (sheet == null) {
                result.getParseErrors().add("Sheet '" + config.getSheetName() + "' not found in workbook.");
                return result;
            }

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            int headerIdx = config.getHeaderRowNumber() - 1; // 0-indexed
            Row headerRow = sheet.getRow(headerIdx);
            if (headerRow == null) {
                result.getParseErrors().add("Header row at index " + config.getHeaderRowNumber() + " is empty.");
                return result;
            }

            // Map column letters to 0-based indices
            int colCat = columnLetterToIndex(config.getCategoryCol());
            int colSubcat = columnLetterToIndex(config.getSubcategoryCol());
            int colProd = columnLetterToIndex(config.getProductNameCol());
            int colSize = columnLetterToIndex(config.getSizeLabelCol());
            int colPrice = columnLetterToIndex(config.getPriceCol());
            int colSrNo = config.getSrNoCol() != null ? columnLetterToIndex(config.getSrNoCol()) : -1;

            Set<String> seenCompositeKeys = new HashSet<>();
            int totalPhysRows = sheet.getLastRowNum();
            int maxRows = config.getMaxRows();

            for (int rIdx = headerIdx + 1; rIdx <= totalPhysRows; rIdx++) {
                if (result.getRows().size() >= maxRows) {
                    result.getParseErrors().add("Maximum allowed row count (" + maxRows + ") exceeded.");
                    break;
                }

                Row row = sheet.getRow(rIdx);
                if (row == null || isRowEmpty(row)) {
                    continue; // Skip blank rows
                }

                int humanRowNumber = rIdx + 1;

                String srNo = colSrNo >= 0 ? getCellStringValue(row.getCell(colSrNo), evaluator) : String.valueOf(humanRowNumber);
                String category = getCellStringValue(row.getCell(colCat), evaluator);
                String subcategory = getCellStringValue(row.getCell(colSubcat), evaluator);
                String productName = getCellStringValue(row.getCell(colProd), evaluator);
                String sizeLabel = getCellStringValue(row.getCell(colSize), evaluator);
                BigDecimal price = getCellNumericValue(row.getCell(colPrice), evaluator);

                // Validation
                if (category == null || category.isBlank()) {
                    result.getParseErrors().add(String.format("Row %d: Category is required and cannot be empty.", humanRowNumber));
                    continue;
                }
                if (category.length() > 120) {
                    result.getParseErrors().add(String.format("Row %d: Category exceeds max length of 120 characters: '%s'", humanRowNumber, category));
                    continue;
                }

                if (subcategory == null || subcategory.isBlank()) {
                    result.getParseErrors().add(String.format("Row %d: Subcategory is required and cannot be empty.", humanRowNumber));
                    continue;
                }
                if (subcategory.length() > 160) {
                    result.getParseErrors().add(String.format("Row %d: Subcategory exceeds max length of 160 characters: '%s'", humanRowNumber, subcategory));
                    continue;
                }

                if (productName == null || productName.isBlank()) {
                    result.getParseErrors().add(String.format("Row %d: Product Name is required and cannot be empty.", humanRowNumber));
                    continue;
                }
                if (productName.length() > 240) {
                    result.getParseErrors().add(String.format("Row %d: Product Name exceeds max length of 240 characters: '%s'", humanRowNumber, productName));
                    continue;
                }

                if (sizeLabel == null || sizeLabel.isBlank()) {
                    result.getParseErrors().add(String.format("Row %d: Size label is required and cannot be empty.", humanRowNumber));
                    continue;
                }
                if (sizeLabel.length() > 120) {
                    result.getParseErrors().add(String.format("Row %d: Size label exceeds max length of 120 characters: '%s'", humanRowNumber, sizeLabel));
                    continue;
                }

                if (price == null) {
                    result.getParseErrors().add(String.format("Row %d: Price is required and cannot be missing or malformed.", humanRowNumber));
                    continue;
                }
                if (price.compareTo(BigDecimal.ZERO) <= 0) {
                    result.getParseErrors().add(String.format("Row %d: Price must be strictly greater than 0.00 (got %s).", humanRowNumber, price));
                    continue;
                }

                // Prevent formula injection in string fields
                category = sanitizeString(category);
                subcategory = sanitizeString(subcategory);
                productName = sanitizeString(productName);
                sizeLabel = sanitizeString(sizeLabel);

                String compositeKey = String.format("%s|%s|%s|%s",
                        category.toLowerCase().trim(),
                        subcategory.toLowerCase().trim(),
                        productName.toLowerCase().trim(),
                        sizeLabel.toLowerCase().trim());

                if (!seenCompositeKeys.add(compositeKey)) {
                    result.getParseErrors().add(String.format("Row %d: Duplicate product variant within workbook: [%s > %s > %s > %s].",
                            humanRowNumber, category, subcategory, productName, sizeLabel));
                    continue;
                }

                String sourceIdentity = String.format("%s > %s > %s > %s", category, subcategory, productName, sizeLabel);

                Map<String, String> rawValues = new HashMap<>();
                rawValues.put("srNo", srNo);
                rawValues.put("category", category);
                rawValues.put("subcategory", subcategory);
                rawValues.put("productName", productName);
                rawValues.put("sizeLabel", sizeLabel);
                rawValues.put("price", price.toPlainString());

                result.getRows().add(ProductImportRow.builder()
                        .rowNumber(humanRowNumber)
                        .sheetName(sheet.getSheetName())
                        .srNo(srNo)
                        .category(category)
                        .subcategory(subcategory)
                        .productName(productName)
                        .sizeLabel(sizeLabel)
                        .price(price.setScale(2, RoundingMode.HALF_UP))
                        .sourceIdentity(sourceIdentity)
                        .rawValues(rawValues)
                        .build());
            }

        } catch (Exception e) {
            result.getParseErrors().add("Failed to parse Excel workbook: " + e.getMessage());
        }

        return result;
    }

    private Sheet findSheet(Workbook workbook, String targetName) {
        if (targetName == null || targetName.isBlank()) {
            return workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
        }
        Sheet sheet = workbook.getSheet(targetName);
        if (sheet != null) {
            return sheet;
        }
        // Case-insensitive fallback
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            if (workbook.getSheetName(i).equalsIgnoreCase(targetName.trim())) {
                return workbook.getSheetAt(i);
            }
        }
        // If only 1 sheet in workbook, use it
        if (workbook.getNumberOfSheets() == 1) {
            return workbook.getSheetAt(0);
        }
        return null;
    }

    private boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    private String getCellStringValue(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            try {
                type = evaluator.evaluateFormulaCell(cell);
            } catch (Exception e) {
                type = cell.getCachedFormulaResultType();
            }
        }

        if (type == CellType.STRING) {
            String val = cell.getStringCellValue();
            return val != null ? val.trim() : null;
        } else if (type == CellType.NUMERIC) {
            return dataFormatter.formatCellValue(cell).trim();
        } else if (type == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        }
        return null;
    }

    private BigDecimal getCellNumericValue(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            try {
                type = evaluator.evaluateFormulaCell(cell);
            } catch (Exception e) {
                type = cell.getCachedFormulaResultType();
            }
        }

        if (type == CellType.NUMERIC) {
            double val = cell.getNumericCellValue();
            return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP);
        } else if (type == CellType.STRING) {
            String str = cell.getStringCellValue();
            if (str == null || str.isBlank()) {
                return null;
            }
            try {
                // Strip currency symbols and commas if present
                String clean = str.replaceAll("[^0-9.]", "").trim();
                return new BigDecimal(clean).setScale(2, RoundingMode.HALF_UP);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private int columnLetterToIndex(String letter) {
        if (letter == null || letter.isBlank()) {
            return 0;
        }
        String upper = letter.trim().toUpperCase();
        int result = 0;
        for (int i = 0; i < upper.length(); i++) {
            result = result * 26 + (upper.charAt(i) - 'A' + 1);
        }
        return result - 1; // 0-indexed
    }

    private String sanitizeString(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        // Prevent CSV/spreadsheet formula injection
        if (trimmed.startsWith("=") || trimmed.startsWith("+") || trimmed.startsWith("-") || trimmed.startsWith("@")) {
            return "'" + trimmed;
        }
        return trimmed;
    }
}
