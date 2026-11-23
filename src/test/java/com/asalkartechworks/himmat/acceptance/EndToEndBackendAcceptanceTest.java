package com.asalkartechworks.himmat.acceptance;

import com.asalkartechworks.himmat.billing.dto.CreateCustomerReceiptRequest;
import com.asalkartechworks.himmat.billing.dto.CreateCustomerRefundRequest;
import com.asalkartechworks.himmat.billing.dto.CreateInvoiceRequest;
import com.asalkartechworks.himmat.billing.dto.CustomerReturnLineRequest;
import com.asalkartechworks.himmat.billing.dto.CustomerReturnRequest;
import com.asalkartechworks.himmat.billing.dto.InvoiceLineRequest;
import com.asalkartechworks.himmat.billing.dto.InvoiceRevisionLineRequest;
import com.asalkartechworks.himmat.billing.dto.InvoiceRevisionRequest;
import com.asalkartechworks.himmat.catalog.imports.ProductImportConfig;
import com.asalkartechworks.himmat.catalog.imports.ProductImportResult;
import com.asalkartechworks.himmat.catalog.imports.ProductImportService;
import com.asalkartechworks.himmat.catalog.model.Brand;
import com.asalkartechworks.himmat.catalog.model.Category;
import com.asalkartechworks.himmat.catalog.model.ImportMode;
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
import com.asalkartechworks.himmat.identity.model.AppUser;
import com.asalkartechworks.himmat.identity.model.UserRole;
import com.asalkartechworks.himmat.identity.repository.AppUserRepository;
import com.asalkartechworks.himmat.party.model.Customer;
import com.asalkartechworks.himmat.party.model.CustomerSite;
import com.asalkartechworks.himmat.party.model.CustomerType;
import com.asalkartechworks.himmat.party.model.Supplier;
import com.asalkartechworks.himmat.party.repository.CustomerRepository;
import com.asalkartechworks.himmat.party.repository.CustomerSiteRepository;
import com.asalkartechworks.himmat.party.repository.SupplierRepository;
import com.asalkartechworks.himmat.purchase.dto.CreatePurchaseRequest;
import com.asalkartechworks.himmat.purchase.dto.CreatePurchaseReturnRequest;
import com.asalkartechworks.himmat.purchase.dto.CreateSupplierPaymentRequest;
import com.asalkartechworks.himmat.purchase.dto.CreateSupplierRefundRequest;
import com.asalkartechworks.himmat.purchase.dto.PaymentComponentDto;
import com.asalkartechworks.himmat.purchase.dto.PurchaseLineRequest;
import com.asalkartechworks.himmat.purchase.dto.PurchaseReturnLineRequest;
import com.asalkartechworks.himmat.purchase.model.PaymentMethod;
import com.asalkartechworks.himmat.purchase.model.PriceBasis;
import com.asalkartechworks.himmat.security.JwtService;
import com.asalkartechworks.himmat.security.UserPrincipal;
import com.asalkartechworks.himmat.settings.model.ShopSettings;
import com.asalkartechworks.himmat.settings.repository.ShopSettingsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class EndToEndBackendAcceptanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private ShopSettingsRepository shopSettingsRepository;

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
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerSiteRepository customerSiteRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private ProductImportService importService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String ownerToken;
    private String managerToken;
    private AppUser ownerUser;
    private AppUser managerUser;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE catalog_import_row, catalog_import_run, operational_expense_component, operational_expense, customer_refund_source, customer_refund_component, customer_refund, customer_return_line, customer_return, sales_invoice_revision_line, sales_invoice_revision, customer_ledger_entry, receipt_allocation, customer_receipt_component, customer_receipt, sales_invoice_line, sales_invoice, customer_site, customer, supplier_ledger_entry, payment_allocation, payment_component, supplier_refund, purchase_return_line, purchase_return, supplier_payment, purchase_line, purchase, stock_movement, inventory_balance, product_variant, product, subcategory, category, brand, supplier, business_event, app_user CASCADE");

        Instant now = Instant.now();

        // Step A: Foundation (Owner, Manager, Shop Settings)
        userRepository.findByUsername("e2e_owner").ifPresent(userRepository::delete);
        userRepository.findByUsername("e2e_manager").ifPresent(userRepository::delete);

        ownerUser = userRepository.save(AppUser.builder()
                .username("e2e_owner")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .displayName("Himmat Patel (Owner)")
                .role(UserRole.OWNER)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        managerUser = userRepository.save(AppUser.builder()
                .username("e2e_manager")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .displayName("Suresh Mehta (Manager)")
                .role(UserRole.MANAGER)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        ownerToken = "Bearer " + jwtService.generateToken(UserPrincipal.create(ownerUser));
        managerToken = "Bearer " + jwtService.generateToken(UserPrincipal.create(managerUser));

        shopSettingsRepository.deleteAll();
        shopSettingsRepository.save(ShopSettings.builder()
                .id(ShopSettings.SINGLETON_ID)
                .name("Himmat Enterprises")
                .address("Shop No. 4, Market Yard, Pune, Maharashtra 411037")
                .phone("020-24455667")
                .email("himmat.enterprises@example.com")
                .gstin("27AABCH1234D1Z5")
                .stateCode("27")
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private byte[] createSyntheticWorkbook(List<List<Object>> rows) throws IOException {
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
    @DisplayName("Complete BE01-BE08 End-to-End Acceptance Scenario with Financial, Inventory & Audit Invariants")
    void testEndToEndBackendAcceptanceScenario() throws Exception {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        // -------------------------------------------------------------
        // Step B: Catalog Import & Known Cost Variant
        // -------------------------------------------------------------
        // 1. Import synthetic MRP product with no cost/GST (MRP = 500.00)
        byte[] excelBytes = createSyntheticWorkbook(List.of(
                List.of("Sr No", "Price List", "Sub Category", "Product Name", "Size", "Price (INR)"),
                List.of(1, "Sanitary", "PVC Fittings", "Coupler Socket", "50mm", 500.00)
        ));

        ProductImportConfig config = ProductImportConfig.builder()
                .brandName("Finolex Sanitary")
                .sheetName("Price List")
                .build();

        ProductImportResult importRes = importService.runImportBytes("acceptance_import.xlsx", excelBytes, ImportMode.CREATE_ONLY, "e2e:tester", config);
        assertThat(importRes.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(importRes.getCreatedRows()).isEqualTo(1);

        // Find imported variant
        Brand impBrand = brandRepository.findByNameIgnoreCase("Finolex Sanitary").orElseThrow();
        Category impCat = categoryRepository.findByNameIgnoreCase("Sanitary").orElseThrow();
        Subcategory impSubcat = subcategoryRepository.findByCategoryIdAndNameIgnoreCase(impCat.getId(), "PVC Fittings").orElseThrow();
        Product impProduct = productRepository.findByBrandIdAndSubcategoryIdAndNameIgnoreCase(impBrand.getId(), impSubcat.getId(), "Coupler Socket").orElseThrow();
        ProductVariant importedVariant = productVariantRepository.findByProductIdAndSizeLabelIgnoreCase(impProduct.getId(), "50mm").orElseThrow();

        assertThat(importedVariant.getMrpInclTax()).isEqualByComparingTo("500.00");
        assertThat(importedVariant.getPurchaseCostExclTax()).isNull(); // Unknown cost
        assertThat(importedVariant.getGstRatePct()).isNull();          // Unknown GST

        // Verify zero initialized stock
        InventoryBalance impBalance = inventoryBalanceRepository.findById(importedVariant.getId()).orElseThrow();
        assertThat(impBalance.getQuantityOnHand()).isEqualTo(0);
        assertThat(impBalance.isOpeningConfirmed()).isFalse();

        // 2. Create another product with known cost and tax for margin tests
        Brand asianBrand = brandRepository.save(Brand.builder().name("Asian Paints").isActive(true).version(0L).createdAt(now).updatedAt(now).build());
        Category enamelCat = categoryRepository.save(Category.builder().name("Enamels").isActive(true).version(0L).createdAt(now).updatedAt(now).build());
        Subcategory glossSubcat = subcategoryRepository.save(Subcategory.builder().category(enamelCat).name("High Gloss").isActive(true).version(0L).createdAt(now).updatedAt(now).build());
        Product enamelProduct = productRepository.save(Product.builder().brand(asianBrand).subcategory(glossSubcat).name("Apcolite Gloss").isActive(true).version(0L).createdAt(now).updatedAt(now).build());

        ProductVariant knownCostVariant = productVariantRepository.save(ProductVariant.builder()
                .product(enamelProduct)
                .sku("APC-GLOSS-WHITE-1L")
                .sizeLabel("1 Liter")
                .pricingMethod(PricingMethod.FIXED)
                .fixedPriceInclTax(new BigDecimal("300.00"))
                .purchaseCostExclTax(new BigDecimal("200.00")) // Known Cost
                .gstRatePct(new BigDecimal("18.00"))           // Known GST
                .lowStockThreshold(5)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        inventoryBalanceRepository.save(InventoryBalance.builder()
                .variantId(knownCostVariant.getId())
                .variant(knownCostVariant)
                .quantityOnHand(0)
                .openingConfirmed(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // -------------------------------------------------------------
        // Step C: Parties (Contractor with 2 Sites, 1 Supplier)
        // -------------------------------------------------------------
        Customer contractor = customerRepository.save(Customer.builder()
                .customerCode("CUST-E2E01")
                .name("Ramesh Builders & Contractors")
                .normalizedName("ramesh builders & contractors")
                .phone("9822334455")
                .customerType(CustomerType.CONTRACTOR)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        CustomerSite siteA = customerSiteRepository.save(CustomerSite.builder()
                .customer(contractor)
                .siteName("Green Valley Villa 101")
                .normalizedSiteName("green valley villa 101")
                .addressLine1("Baner, Pune")
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        CustomerSite siteB = customerSiteRepository.save(CustomerSite.builder()
                .customer(contractor)
                .siteName("Blue Ridge Tower B")
                .normalizedSiteName("blue ridge tower b")
                .addressLine1("Hinjawadi, Pune")
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        Supplier supplier = supplierRepository.save(Supplier.builder()
                .supplierCode("SUP-E2E01")
                .name("Apex Hardware Wholesale")
                .normalizedName("apex hardware wholesale")
                .phone("020-25667788")
                .contactPerson("Dinesh Shah")
                .gstin("27AAACA9999A1Z9")
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // -------------------------------------------------------------
        // Step D: Inward Purchase (Restock Stock & Supplier Financials)
        // -------------------------------------------------------------
        // Purchase 50 units of knownCostVariant @ 200.00 excl tax (18% GST -> 236.00 gross -> 11,800.00)
        // and 20 units of importedVariant @ 350.00 excl tax (18% GST -> 413.00 gross -> 8,260.00)
        // Total Payable = 20,060.00
        CreatePurchaseRequest purchaseReq = CreatePurchaseRequest.builder()
                .supplierId(supplier.getId())
                .purchaseDate(today)
                .supplierInvoiceNumber("APEX-INV-7788")
                .lines(List.of(
                        PurchaseLineRequest.builder()
                                .variantId(knownCostVariant.getId())
                                .quantity(50)
                                .priceBasis(PriceBasis.GST_EXCLUSIVE)
                                .enteredUnitPrice(new BigDecimal("200.00"))
                                .gstRatePct(new BigDecimal("18.00"))
                                .build(),
                        PurchaseLineRequest.builder()
                                .variantId(importedVariant.getId())
                                .quantity(20)
                                .priceBasis(PriceBasis.GST_EXCLUSIVE)
                                .enteredUnitPrice(new BigDecimal("350.00"))
                                .gstRatePct(new BigDecimal("18.00"))
                                .build()
                ))
                .build();

        MvcResult purResult = mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(purchaseReq)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode purJson = objectMapper.readTree(purResult.getResponse().getContentAsString()).path("data");
        long purchaseId = purJson.path("id").asLong();
        long pLineKnownId = purJson.path("lines").get(0).path("id").asLong();
        BigDecimal totalPurchasePayable = new BigDecimal(purJson.path("totalPayableAmount").asText());

        // Invariant Check: Stock increased by purchase
        assertThat(inventoryBalanceRepository.findById(knownCostVariant.getId()).get().getQuantityOnHand()).isEqualTo(50);
        assertThat(inventoryBalanceRepository.findById(importedVariant.getId()).get().getQuantityOnHand()).isEqualTo(20);

        // Record Partial Supplier Payment: 10,000.00 (split Cash 4,000 + Online 6,000)
        CreateSupplierPaymentRequest supPayReq = CreateSupplierPaymentRequest.builder()
                .paymentDate(today)
                .totalAmount(new BigDecimal("10000.00"))
                .components(List.of(
                        PaymentComponentDto.builder().method(PaymentMethod.CASH).amount(new BigDecimal("4000.00")).build(),
                        PaymentComponentDto.builder().method(PaymentMethod.ONLINE).amount(new BigDecimal("6000.00")).onlineReference("UTR-APEX-01").build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/suppliers/" + supplier.getId() + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(supPayReq)))
                .andExpect(status().isCreated());

        // Verify supplier account summary
        mockMvc.perform(get("/api/v1/suppliers/" + supplier.getId() + "/account-summary")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPurchases", is(totalPurchasePayable.toPlainString())))
                .andExpect(jsonPath("$.data.totalPaid", is("10000.00")))
                .andExpect(jsonPath("$.data.currentBalance", is(totalPurchasePayable.subtract(new BigDecimal("10000.00")).toPlainString())));

        // -------------------------------------------------------------
        // Step E: Billing Site A (Direct Customer Sales Invoice)
        // -------------------------------------------------------------
        // Bill: 10 units of knownCostVariant @ 300.00 = 3,000.00
        CreateInvoiceRequest invAReq = CreateInvoiceRequest.builder()
                .customerId(contractor.getId())
                .siteId(siteA.getId())
                .invoiceDate(today)
                .lines(List.of(
                        InvoiceLineRequest.builder().variantId(knownCostVariant.getId()).quantity(10).build()
                ))
                .build();

        MvcResult invAResult = mockMvc.perform(post("/api/v1/invoices")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invAReq)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode invAJson = objectMapper.readTree(invAResult.getResponse().getContentAsString()).path("data");
        long invoiceAId = invAJson.path("id").asLong();
        long lineAId = invAJson.path("lines").get(0).path("id").asLong();
        String originalInvoiceDate = invAJson.path("invoiceDate").asText();

        // Invariant Check: Stock deducted (50 - 10 = 40)
        assertThat(inventoryBalanceRepository.findById(knownCostVariant.getId()).get().getQuantityOnHand()).isEqualTo(40);

        // Record Partial Mixed Customer Payment: 2,000.00 (Cash 1,000 + Online 1,000)
        CreateCustomerReceiptRequest recReq = CreateCustomerReceiptRequest.builder()
                .receiptDate(today)
                .totalAmount(new BigDecimal("2000.00"))
                .components(List.of(
                        PaymentComponentDto.builder().method(PaymentMethod.CASH).amount(new BigDecimal("1000.00")).build(),
                        PaymentComponentDto.builder().method(PaymentMethod.ONLINE).amount(new BigDecimal("1000.00")).onlineReference("UPI-CONTRACTOR-1").build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/customers/" + contractor.getId() + "/receipts")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(recReq)))
                .andExpect(status().isCreated());

        // Verify Customer Balance: 3,000 - 2,000 = 1,000
        mockMvc.perform(get("/api/v1/customers/" + contractor.getId() + "/account")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBalance", is("1000.00")));

        // -------------------------------------------------------------
        // Step F: Separate Invoice for Site B (Shared Customer Account)
        // -------------------------------------------------------------
        // Bill for Site B: 5 units of importedVariant (MRP 500.00) = 2,500.00
        CreateInvoiceRequest invBReq = CreateInvoiceRequest.builder()
                .customerId(contractor.getId())
                .siteId(siteB.getId())
                .invoiceDate(today)
                .lines(List.of(
                        InvoiceLineRequest.builder().variantId(importedVariant.getId()).quantity(5).build()
                ))
                .build();

        MvcResult invBResult = mockMvc.perform(post("/api/v1/invoices")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invBReq)))
                .andExpect(status().isCreated())
                .andReturn();

        long invoiceBId = objectMapper.readTree(invBResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        // Customer balance is now: 1,000 + 2,500 = 3,500
        mockMvc.perform(get("/api/v1/customers/" + contractor.getId() + "/account")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBalance", is("3500.00")));

        // -------------------------------------------------------------
        // Step G: Unambiguous Invoice Revision on Invoice A
        // -------------------------------------------------------------
        // Customer takes 2 more units (quantity: 10 -> 12). Payable: 3,000 -> 3,600.
        InvoiceRevisionRequest revReq = InvoiceRevisionRequest.builder()
                .expectedVersion(0L)
                .reason("Contractor requested 2 extra paint tins on Site A")
                .lines(List.of(
                        InvoiceRevisionLineRequest.builder()
                                .invoiceLineId(lineAId)
                                .variantId(knownCostVariant.getId())
                                .quantity(12)
                                .build()
                ))
                .build();

        MvcResult revResult = mockMvc.perform(post("/api/v1/invoices/" + invoiceAId + "/revisions")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revReq)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode revJson = objectMapper.readTree(revResult.getResponse().getContentAsString()).path("data");
        assertThat(revJson.path("deltaPayableAmount").asText()).isEqualTo("600.00");
        long revisionId = revJson.path("id").asLong();

        // Invariant Check: Stock deducted by delta 2 units (40 - 2 = 38)
        assertThat(inventoryBalanceRepository.findById(knownCostVariant.getId()).get().getQuantityOnHand()).isEqualTo(38);

        // Invariant Check: Original invoice date preserved
        mockMvc.perform(get("/api/v1/invoices/" + invoiceAId).header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceDate", is(originalInvoiceDate)))
                .andExpect(jsonPath("$.data.totalPayableAmount", is("3600.00")));

        // -------------------------------------------------------------
        // Step H: Customer Return (Restores Stock & Reconciles Funds)
        // -------------------------------------------------------------
        // Return 2 units from Invoice A. Refund credit = 600.00.
        CustomerReturnRequest retReq = CustomerReturnRequest.builder()
                .returnDate(today)
                .reason("Unused surplus from painter")
                .lines(List.of(
                        CustomerReturnLineRequest.builder()
                                .invoiceLineId(lineAId)
                                .quantity(2)
                                .build()
                ))
                .build();

        MvcResult retResult = mockMvc.perform(post("/api/v1/invoices/" + invoiceAId + "/returns")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(retReq)))
                .andExpect(status().isCreated())
                .andReturn();

        long customerReturnId = objectMapper.readTree(retResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        // Invariant Check: Stock restored by 2 units (38 + 2 = 40)
        assertThat(inventoryBalanceRepository.findById(knownCostVariant.getId()).get().getQuantityOnHand()).isEqualTo(40);

        // -------------------------------------------------------------
        // Step I: Supplier Return & Supplier Refund
        // -------------------------------------------------------------
        // Return 5 units of knownCostVariant to supplier
        CreatePurchaseReturnRequest pretReq = CreatePurchaseReturnRequest.builder()
                .returnDate(today)
                .reason("Surplus batch return")
                .lines(List.of(
                        PurchaseReturnLineRequest.builder()
                                .purchaseLineId(pLineKnownId)
                                .quantity(5)
                                .build()
                ))
                .build();

        MvcResult pretResult = mockMvc.perform(post("/api/v1/purchases/" + purchaseId + "/returns")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pretReq)))
                .andExpect(status().isCreated())
                .andReturn();

        long purchaseReturnId = objectMapper.readTree(pretResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        // Invariant Check: Stock deducted by supplier return (40 - 5 = 35)
        assertThat(inventoryBalanceRepository.findById(knownCostVariant.getId()).get().getQuantityOnHand()).isEqualTo(35);

        // Record Supplier Refund: 500.00 online
        CreateSupplierRefundRequest srefReq = CreateSupplierRefundRequest.builder()
                .refundDate(today)
                .reason("Supplier return adjustment rebate")
                .totalAmount(new BigDecimal("500.00"))
                .components(List.of(
                        PaymentComponentDto.builder().method(PaymentMethod.ONLINE).amount(new BigDecimal("500.00")).onlineReference("SRF-ONLINE-1").build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/suppliers/" + supplier.getId() + "/refunds")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(srefReq)))
                .andExpect(status().isCreated());

        // -------------------------------------------------------------
        // Step J: Customer Advance Credit Refund
        // -------------------------------------------------------------
        // Pay a customer receipt of 1000.00 that generates advance credit, then disburse refund of 200.00
        CreateCustomerReceiptRequest advRecReq = CreateCustomerReceiptRequest.builder()
                .receiptDate(today)
                .totalAmount(new BigDecimal("4000.00")) // Overpays customer dues, generating credit
                .components(List.of(PaymentComponentDto.builder().method(PaymentMethod.CASH).amount(new BigDecimal("4000.00")).build()))
                .build();

        mockMvc.perform(post("/api/v1/customers/" + contractor.getId() + "/receipts")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(advRecReq)))
                .andExpect(status().isCreated());

        // Disburse refund of 200.00 cash
        CreateCustomerRefundRequest refReq = CreateCustomerRefundRequest.builder()
                .refundDate(today)
                .reason("Advance refund payout")
                .totalAmount(new BigDecimal("200.00"))
                .components(List.of(PaymentComponentDto.builder().method(PaymentMethod.CASH).amount(new BigDecimal("200.00")).build()))
                .build();

        MvcResult refResult = mockMvc.perform(post("/api/v1/customers/" + contractor.getId() + "/refunds")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refReq)))
                .andExpect(status().isCreated())
                .andReturn();

        long customerRefundId = objectMapper.readTree(refResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        // -------------------------------------------------------------
        // Step K: Reporting & Invariants Verification
        // -------------------------------------------------------------
        // 1. Dashboard summary
        mockMvc.perform(get("/api/v1/dashboard/summary").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grossSales", notNullValue()))
                .andExpect(jsonPath("$.data.netSales", notNullValue()))
                .andExpect(jsonPath("$.data.profitSummary", notNullValue()))
                .andExpect(jsonPath("$.data.profitSummary.unknownCostSales", notNullValue()));

        // 2. Profit report: Owner allowed, Manager forbidden
        mockMvc.perform(get("/api/v1/reports/profit").header(HttpHeaders.AUTHORIZATION, managerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/reports/profit").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.salesValueExcludedUnknownCost", notNullValue()))
                .andExpect(jsonPath("$.data.costCoveragePct", notNullValue()));

        // 3. Cash movements report: contains cash in, out, and online components
        mockMvc.perform(get("/api/v1/reports/cash-movements").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCashIn", notNullValue()))
                .andExpect(jsonPath("$.data.totalCashOut", notNullValue()))
                .andExpect(jsonPath("$.data.netCashMovement", notNullValue()));

        // -------------------------------------------------------------
        // Step L: PDF Document Endpoints (200 OK & Content-Type application/pdf)
        // -------------------------------------------------------------
        mockMvc.perform(get("/api/v1/invoices/" + invoiceAId + "/pdf?format=NORMAL").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mockMvc.perform(get("/api/v1/invoices/" + invoiceAId + "/pdf?format=GST").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mockMvc.perform(get("/api/v1/invoices/" + invoiceAId + "/revisions/" + revisionId + "/pdf").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mockMvc.perform(get("/api/v1/customer-returns/" + customerReturnId + "/pdf").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mockMvc.perform(get("/api/v1/customer-refunds/" + customerRefundId + "/pdf").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mockMvc.perform(get("/api/v1/customers/" + contractor.getId() + "/ledger/pdf").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mockMvc.perform(get("/api/v1/purchases/" + purchaseId + "/pdf").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mockMvc.perform(get("/api/v1/purchases/returns/" + purchaseReturnId + "/pdf").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mockMvc.perform(get("/api/v1/suppliers/" + supplier.getId() + "/ledger/pdf").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        // -------------------------------------------------------------
        // Step M: Concurrency & Failure Invariants
        // -------------------------------------------------------------
        // Invariant 1: Stale version check on invoice revision
        InvoiceRevisionRequest staleRevReq = InvoiceRevisionRequest.builder()
                .expectedVersion(0L) // Invoice version has already incremented past 0
                .reason("Stale revision attempt")
                .lines(List.of(
                        InvoiceRevisionLineRequest.builder()
                                .invoiceLineId(lineAId)
                                .variantId(knownCostVariant.getId())
                                .quantity(15)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/invoices/" + invoiceAId + "/revisions")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(staleRevReq)))
                .andExpect(status().isConflict());

        // Invariant 2: Non-negative stock (cannot sell more than on-hand)
        CreateInvoiceRequest oversellReq = CreateInvoiceRequest.builder()
                .customerId(contractor.getId())
                .invoiceDate(today)
                .lines(List.of(
                        InvoiceLineRequest.builder()
                                .variantId(knownCostVariant.getId())
                                .quantity(9999) // Way more than on hand
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/invoices")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oversellReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("INSUFFICIENT_STOCK")));
    }
}
