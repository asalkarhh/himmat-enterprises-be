package com.asalkartechworks.himmat.catalog;

import com.asalkartechworks.himmat.catalog.imports.ProductImportConfig;
import com.asalkartechworks.himmat.catalog.imports.ProductImportResult;
import com.asalkartechworks.himmat.catalog.imports.ProductImportService;
import com.asalkartechworks.himmat.catalog.model.Brand;
import com.asalkartechworks.himmat.catalog.model.Category;
import com.asalkartechworks.himmat.catalog.model.ImportMode;
import com.asalkartechworks.himmat.catalog.model.ImportRowAction;
import com.asalkartechworks.himmat.catalog.model.ImportStatus;
import com.asalkartechworks.himmat.catalog.model.InventoryBalance;
import com.asalkartechworks.himmat.catalog.model.PricingMethod;
import com.asalkartechworks.himmat.catalog.model.Product;
import com.asalkartechworks.himmat.catalog.model.ProductVariant;
import com.asalkartechworks.himmat.catalog.model.Subcategory;
import com.asalkartechworks.himmat.catalog.repository.BrandRepository;
import com.asalkartechworks.himmat.catalog.repository.CategoryRepository;
import com.asalkartechworks.himmat.catalog.repository.InventoryBalanceRepository;
import com.asalkartechworks.himmat.catalog.repository.ProductRepository;
import com.asalkartechworks.himmat.catalog.repository.ProductVariantRepository;
import com.asalkartechworks.himmat.catalog.repository.SubcategoryRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class ProductImportTest {

    @Autowired
    private ProductImportService importService;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SubcategoryRepository subcategoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private InventoryBalanceRepository inventoryBalanceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // Clean catalog and import tracking tables safely
        jdbcTemplate.execute("DELETE FROM catalog_import_row");
        jdbcTemplate.execute("DELETE FROM catalog_import_run");
    }

    private byte[] createTestWorkbook(List<List<Object>> rows) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Price List");
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r);
                List<Object> rowData = rows.get(r);
                for (int c = 0; c < rowData.size(); c++) {
                    Object val = rowData.get(c);
                    Cell cell = row.createCell(c);
                    if (val instanceof Number num) {
                        cell.setCellValue(num.doubleValue());
                    } else if (val != null) {
                        cell.setCellValue(val.toString());
                    }
                }
            }
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    @Test
    @DisplayName("Dry run mode should parse, validate and plan without modifying catalog or inventory")
    void testDryRun_writesNoCatalogData() throws Exception {
        byte[] excelBytes = createTestWorkbook(List.of(
                List.of("Sr No", "Price List", "Sub Category", "Product Name", "Size", "Price (INR)"),
                List.of(1, "PVC Pipes", "Rigid Conduit", "Heavy Duty Conduit", "20mm", 125.50),
                List.of(2, "PVC Pipes", "Rigid Conduit", "Heavy Duty Conduit", "25mm", 165.00)
        ));

        long brandCountBefore = brandRepository.count();
        long variantCountBefore = productVariantRepository.count();
        long balanceCountBefore = inventoryBalanceRepository.count();

        ProductImportConfig config = ProductImportConfig.builder()
                .brandName("Finolex Test")
                .sheetName("Price List")
                .build();

        ProductImportResult result = importService.runImportBytes("dry_run_test.xlsx", excelBytes, ImportMode.DRY_RUN, "test:operator", config);

        assertThat(result.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getCreatedRows()).isEqualTo(2); // Planned creates
        assertThat(result.getRejectedRows()).isEqualTo(0);

        // Catalog tables must NOT have changed!
        assertThat(brandRepository.count()).isEqualTo(brandCountBefore);
        assertThat(productVariantRepository.count()).isEqualTo(variantCountBefore);
        assertThat(inventoryBalanceRepository.count()).isEqualTo(balanceCountBefore);
    }

    @Test
    @DisplayName("Create-only mode should import valid rows, create variants with zero stock, leaving unknown cost/tax null")
    void testCreateOnly_createsRecordsWithZeroStock() throws Exception {
        byte[] excelBytes = createTestWorkbook(List.of(
                List.of("Sr No", "Price List", "Sub Category", "Product Name", "Size", "Price (INR)"),
                List.of(1, "ASTM White", "Plain Pipe", "SCH40 Pipe 3M", "1/2\"", 450.00),
                List.of(2, "ASTM White", "Plain Pipe", "SCH40 Pipe 3M", "3/4\"", 620.00)
        ));

        ProductImportConfig config = ProductImportConfig.builder()
                .brandName("Finolex Plumbing")
                .sheetName("Price List")
                .build();

        ProductImportResult result = importService.runImportBytes("create_test.xlsx", excelBytes, ImportMode.CREATE_ONLY, "test:operator", config);

        assertThat(result.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getCreatedRows()).isEqualTo(2);
        assertThat(result.getRejectedRows()).isEqualTo(0);

        // Verify Brand
        Optional<Brand> brand = brandRepository.findByNameIgnoreCase("Finolex Plumbing");
        assertThat(brand).isPresent();

        // Verify Category
        Optional<Category> category = categoryRepository.findByNameIgnoreCase("ASTM White");
        assertThat(category).isPresent();

        // Verify Variant
        Optional<Product> prodOpt = productRepository.findByBrandIdAndSubcategoryIdAndNameIgnoreCase(
                brand.get().getId(),
                subcategoryRepository.findByCategoryIdAndNameIgnoreCase(category.get().getId(), "Plain Pipe").get().getId(),
                "SCH40 Pipe 3M"
        );
        assertThat(prodOpt).isPresent();

        Optional<ProductVariant> v1 = productVariantRepository.findByProductIdAndSizeLabelIgnoreCase(prodOpt.get().getId(), "1/2\"");
        assertThat(v1).isPresent();
        assertThat(v1.get().getPricingMethod()).isEqualTo(PricingMethod.MRP_DISCOUNT);
        assertThat(v1.get().getMrpInclTax()).isEqualByComparingTo("450.00");
        assertThat(v1.get().getPurchaseCostExclTax()).isNull(); // Strictly null (unknown)
        assertThat(v1.get().getGstRatePct()).isNull();          // Strictly null (unknown)
        assertThat(v1.get().getProfitPct()).isNull();           // Strictly null (unknown)
        assertThat(v1.get().isActive()).isTrue();

        // Verify 1:1 Inventory Balance initialized to 0
        Optional<InventoryBalance> balance = inventoryBalanceRepository.findById(v1.get().getId());
        assertThat(balance).isPresent();
        assertThat(balance.get().getQuantityOnHand()).isEqualTo(0);
        assertThat(balance.get().isOpeningConfirmed()).isFalse();
    }

    @Test
    @DisplayName("Exact rerun in create-only mode should result in zero duplicates and mark all rows unchanged")
    void testExactRerun_createsNoDuplicates() throws Exception {
        byte[] excelBytes = createTestWorkbook(List.of(
                List.of("Sr No", "Price List", "Sub Category", "Product Name", "Size", "Price (INR)"),
                List.of(1, "CPVC", "CPVC Fittings", "Brass Elbow 90D", "1/2\"", 185.00)
        ));

        ProductImportConfig config = ProductImportConfig.builder()
                .brandName("Finolex CPVC")
                .sheetName("Price List")
                .build();

        // First run
        ProductImportResult res1 = importService.runImportBytes("rerun_test.xlsx", excelBytes, ImportMode.CREATE_ONLY, "test:operator", config);
        assertThat(res1.getCreatedRows()).isEqualTo(1);

        long variantCountAfterFirstRun = productVariantRepository.count();

        // Second run with same file and mode
        ProductImportResult res2 = importService.runImportBytes("rerun_test.xlsx", excelBytes, ImportMode.CREATE_ONLY, "test:operator", config);
        assertThat(res2.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(res2.getCreatedRows()).isEqualTo(0);
        assertThat(res2.getUnchangedRows()).isEqualTo(1);
        assertThat(res2.getRejectedRows()).isEqualTo(0);

        // Variant count must remain identical
        assertThat(productVariantRepository.count()).isEqualTo(variantCountAfterFirstRun);
    }

    @Test
    @DisplayName("Conflicting price in create-only mode should report conflict and reject row without overwriting")
    void testCreateOnly_withConflictingPrice_reportsConflict() throws Exception {
        byte[] excelBytes1 = createTestWorkbook(List.of(
                List.of("Sr No", "Price List", "Sub Category", "Product Name", "Size", "Price (INR)"),
                List.of(1, "Agri", "Agri Pipes", "Selfit Agri Pipe 6M", "63mm", 850.00)
        ));

        ProductImportConfig config = ProductImportConfig.builder()
                .brandName("Finolex Agri")
                .sheetName("Price List")
                .build();

        // Initial create
        importService.runImportBytes("agri_v1.xlsx", excelBytes1, ImportMode.CREATE_ONLY, "test:operator", config);

        // Attempt second create-only run with different price for same item
        byte[] excelBytes2 = createTestWorkbook(List.of(
                List.of("Sr No", "Price List", "Sub Category", "Product Name", "Size", "Price (INR)"),
                List.of(1, "Agri", "Agri Pipes", "Selfit Agri Pipe 6M", "63mm", 920.00) // Price changed from 850 to 920!
        ));

        ProductImportResult res2 = importService.runImportBytes("agri_v2.xlsx", excelBytes2, ImportMode.CREATE_ONLY, "test:operator", config);

        assertThat(res2.getStatus()).isEqualTo(ImportStatus.CONFLICT);
        assertThat(res2.getCreatedRows()).isEqualTo(0);
        assertThat(res2.getRejectedRows()).isEqualTo(1);
        assertThat(res2.getErrorSummary()).contains("Variant exists with different MRP");

        // Verify DB price was NOT overwritten
        Brand brand = brandRepository.findByNameIgnoreCase("Finolex Agri").get();
        Subcategory subcat = subcategoryRepository.findByCategoryIdAndNameIgnoreCase(
                categoryRepository.findByNameIgnoreCase("Agri").get().getId(), "Agri Pipes").get();
        Product prod = productRepository.findByBrandIdAndSubcategoryIdAndNameIgnoreCase(brand.getId(), subcat.getId(), "Selfit Agri Pipe 6M").get();
        ProductVariant variant = productVariantRepository.findByProductIdAndSizeLabelIgnoreCase(prod.getId(), "63mm").get();

        assertThat(variant.getMrpInclTax()).isEqualByComparingTo("850.00");
    }

    @Test
    @DisplayName("Explicit update mode should update MRP without touching cost, GST rate or active status")
    void testExplicitUpdate_updatesPriceOnly() throws Exception {
        byte[] excelBytes1 = createTestWorkbook(List.of(
                List.of("Sr No", "Price List", "Sub Category", "Product Name", "Size", "Price (INR)"),
                List.of(1, "SWR", "SWR Fittings", "Door Bend 87.5D", "110mm", 310.00)
        ));

        ProductImportConfig config = ProductImportConfig.builder()
                .brandName("Finolex SWR")
                .sheetName("Price List")
                .build();

        importService.runImportBytes("swr_v1.xlsx", excelBytes1, ImportMode.CREATE_ONLY, "test:operator", config);

        // Simulate manager manually maintaining purchase cost and GST rate on this imported product
        Brand brand = brandRepository.findByNameIgnoreCase("Finolex SWR").get();
        Subcategory subcat = subcategoryRepository.findByCategoryIdAndNameIgnoreCase(
                categoryRepository.findByNameIgnoreCase("SWR").get().getId(), "SWR Fittings").get();
        Product prod = productRepository.findByBrandIdAndSubcategoryIdAndNameIgnoreCase(brand.getId(), subcat.getId(), "Door Bend 87.5D").get();
        ProductVariant variant = productVariantRepository.findByProductIdAndSizeLabelIgnoreCase(prod.getId(), "110mm").get();

        variant.setPurchaseCostExclTax(new BigDecimal("220.00"));
        variant.setGstRatePct(new BigDecimal("18.00"));
        variant.setLowStockThreshold(10);
        productVariantRepository.save(variant);

        // Run explicit update with new MRP
        byte[] excelBytes2 = createTestWorkbook(List.of(
                List.of("Sr No", "Price List", "Sub Category", "Product Name", "Size", "Price (INR)"),
                List.of(1, "SWR", "SWR Fittings", "Door Bend 87.5D", "110mm", 345.00) // Updated price
        ));

        ProductImportResult updateRes = importService.runImportBytes("swr_v2.xlsx", excelBytes2, ImportMode.UPDATE, "test:operator", config);

        assertThat(updateRes.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(updateRes.getUpdatedRows()).isEqualTo(1);

        // Verify only MRP was updated; cost, GST rate, and lowStockThreshold remain preserved
        ProductVariant updatedVariant = productVariantRepository.findById(variant.getId()).get();
        assertThat(updatedVariant.getMrpInclTax()).isEqualByComparingTo("345.00");
        assertThat(updatedVariant.getPurchaseCostExclTax()).isEqualByComparingTo("220.00");
        assertThat(updatedVariant.getGstRatePct()).isEqualByComparingTo("18.00");
        assertThat(updatedVariant.getLowStockThreshold()).isEqualTo(10);
    }

    @Test
    @DisplayName("Validation should reject missing prices, negative prices, and duplicate rows within sheet")
    void testValidation_rejectsMalformedRows() throws Exception {
        byte[] excelBytes = createTestWorkbook(List.of(
                List.of("Sr No", "Price List", "Sub Category", "Product Name", "Size", "Price (INR)"),
                List.of(1, "ASTM", "Pipe", "Item A", "10mm", -50.00),   // Negative price
                List.of(2, "", "Pipe", "Item B", "20mm", 100.00),         // Missing category
                List.of(3, "ASTM", "Pipe", "Item C", "30mm", 0.00),      // Zero price
                List.of(4, "ASTM", "Pipe", "Item D", "40mm", 150.00),
                List.of(5, "ASTM", "Pipe", "Item D", "40mm", 180.00)     // Duplicate combo with row 4
        ));

        ProductImportConfig config = ProductImportConfig.builder()
                .brandName("Finolex Validation")
                .sheetName("Price List")
                .build();

        ProductImportResult res = importService.runImportBytes("malformed.xlsx", excelBytes, ImportMode.CREATE_ONLY, "test:operator", config);

        assertThat(res.getStatus()).isEqualTo(ImportStatus.FAILED);
        assertThat(res.getErrorSummary()).contains("Price must be strictly greater than 0.00");
        assertThat(res.getErrorSummary()).contains("Category is required");
        assertThat(res.getErrorSummary()).contains("Duplicate product variant within workbook");
    }

    @Test
    @DisplayName("Real Finolex workbook dry run should successfully parse and plan all 1809 rows with zero validation errors")
    void testRealFinolexWorkbook_dryRun() {
        File realWorkbook = new File("data/Finolex_Combined_Price_List_Billing.xlsx");
        if (!realWorkbook.exists()) {
            return; // Skip if file not located
        }

        ProductImportConfig config = ProductImportConfig.defaultFinolexConfig();
        ProductImportResult result = importService.runImport(realWorkbook, ImportMode.DRY_RUN, "developer:acceptance-check", config);

        assertThat(result.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(result.getTotalRows()).isEqualTo(1809);
        assertThat(result.getRejectedRows()).isEqualTo(0);
        assertThat(result.getCreatedRows()).isEqualTo(1809); // All 1809 rows planned for creation
        assertThat(result.getErrorSummary()).isNull();
    }
}
