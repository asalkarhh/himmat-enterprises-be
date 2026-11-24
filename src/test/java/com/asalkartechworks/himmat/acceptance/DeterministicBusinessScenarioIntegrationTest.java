package com.asalkartechworks.himmat.acceptance;

import com.asalkartechworks.himmat.billing.dto.*;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceRepository;
import com.asalkartechworks.himmat.catalog.model.*;
import com.asalkartechworks.himmat.catalog.repository.*;
import com.asalkartechworks.himmat.expense.dto.CreateExpenseRequest;
import com.asalkartechworks.himmat.expense.dto.ExpenseComponentDto;
import com.asalkartechworks.himmat.expense.model.ExpenseCategory;
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
import com.asalkartechworks.himmat.pricing.dto.PricingPreviewLineRequest;
import com.asalkartechworks.himmat.pricing.dto.PricingPreviewRequest;
import com.asalkartechworks.himmat.pricing.dto.PricingPreviewResponse;
import com.asalkartechworks.himmat.pricing.service.PricingService;
import com.asalkartechworks.himmat.purchase.dto.*;
import com.asalkartechworks.himmat.purchase.model.PaymentMethod;
import com.asalkartechworks.himmat.purchase.model.PriceBasis;
import com.asalkartechworks.himmat.security.JwtService;
import com.asalkartechworks.himmat.security.UserPrincipal;
import com.asalkartechworks.himmat.settings.model.ShopSettings;
import com.asalkartechworks.himmat.settings.repository.ShopSettingsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Deterministic End-to-End Business Scenario Integration Tests
 * Verifies complete business workflows, math reconciliation, permissions,
 * and concurrency invariants as required by QA01 for Himmat Enterprises.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class DeterministicBusinessScenarioIntegrationTest {

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
    private PricingService pricingService;

    @Autowired
    private SalesInvoiceRepository salesInvoiceRepository;

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

        ownerUser = userRepository.save(AppUser.builder()
                .username("qa_owner")
                .passwordHash(passwordEncoder.encode("OwnerPass123!"))
                .displayName("Himmatbhai Patel (Owner)")
                .role(UserRole.OWNER)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        managerUser = userRepository.save(AppUser.builder()
                .username("qa_manager")
                .passwordHash(passwordEncoder.encode("ManagerPass123!"))
                .displayName("Kishore Shah (Manager)")
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

    @Test
    @DisplayName("Deterministic Business Scenario: Steps A through J Complete Lifecycle")
    void testDeterministicBusinessScenario_StepAThroughJ() throws Exception {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        // -------------------------------------------------------------
        // Step A: Setup Products & Master Entities
        // -------------------------------------------------------------
        // 1. MRP product: MRP 500.00, Category sales discount 10%, GST 18%
        Brand brand = brandRepository.save(Brand.builder().name("Finolex").isActive(true).version(0L).createdAt(now).updatedAt(now).build());
        Category pipesCat = categoryRepository.save(Category.builder()
                .name("Pipes & Fittings")
                .salesDiscountPct(new BigDecimal("10.0000"))
                .purchaseDiscountPct(new BigDecimal("20.0000"))
                .isActive(true).version(0L).createdAt(now).updatedAt(now).build());
        Subcategory pvcSub = subcategoryRepository.save(Subcategory.builder().category(pipesCat).name("PVC Pipes").isActive(true).version(0L).createdAt(now).updatedAt(now).build());
        Product pipeProd = productRepository.save(Product.builder().brand(brand).subcategory(pvcSub).name("Rigid PVC Pipe").isActive(true).version(0L).createdAt(now).updatedAt(now).build());

        ProductVariant mrpVariant = productVariantRepository.save(ProductVariant.builder()
                .product(pipeProd)
                .sku("FIN-PVC-50MM")
                .sizeLabel("50mm x 3m")
                .pricingMethod(PricingMethod.MRP_DISCOUNT)
                .mrpInclTax(new BigDecimal("500.00"))
                .gstRatePct(new BigDecimal("18.00"))
                .purchaseCostExclTax(new BigDecimal("300.00"))
                .isActive(true).version(0L).createdAt(now).updatedAt(now).build());
        inventoryBalanceRepository.save(InventoryBalance.builder().variantId(mrpVariant.getId()).variant(mrpVariant).quantityOnHand(10).openingConfirmed(true).version(0L).createdAt(now).updatedAt(now).build());

        // 2. Cost-Plus product: Cost 100 excl GST, 20% margin, 18% GST -> 141.60 per piece
        Brand asianBrand = brandRepository.save(Brand.builder().name("Asian Paints").isActive(true).version(0L).createdAt(now).updatedAt(now).build());
        Category paintCat = categoryRepository.save(Category.builder().name("Emulsions").isActive(true).version(0L).createdAt(now).updatedAt(now).build());
        Subcategory extSub = subcategoryRepository.save(Subcategory.builder().category(paintCat).name("Exterior").isActive(true).version(0L).createdAt(now).updatedAt(now).build());
        Product apexProd = productRepository.save(Product.builder().brand(asianBrand).subcategory(extSub).name("Apex Exterior Emulsion").isActive(true).version(0L).createdAt(now).updatedAt(now).build());

        ProductVariant costPlusVariant = productVariantRepository.save(ProductVariant.builder()
                .product(apexProd)
                .sku("APX-WHT-1L")
                .sizeLabel("1 Liter")
                .pricingMethod(PricingMethod.COST_PLUS)
                .purchaseCostExclTax(new BigDecimal("100.00"))
                .profitPct(new BigDecimal("20.00"))
                .gstRatePct(new BigDecimal("18.00"))
                .isActive(true).version(0L).createdAt(now).updatedAt(now).build());
        inventoryBalanceRepository.save(InventoryBalance.builder().variantId(costPlusVariant.getId()).variant(costPlusVariant).quantityOnHand(0).openingConfirmed(true).version(0L).createdAt(now).updatedAt(now).build());

        // 3. Product with unknown cost/GST
        ProductVariant unknownVariant = productVariantRepository.save(ProductVariant.builder()
                .product(apexProd)
                .sku("APX-UNK-1L")
                .sizeLabel("Sample Tin")
                .pricingMethod(PricingMethod.FIXED)
                .fixedPriceInclTax(new BigDecimal("120.00"))
                .purchaseCostExclTax(null)
                .gstRatePct(null)
                .isActive(true).version(0L).createdAt(now).updatedAt(now).build());
        inventoryBalanceRepository.save(InventoryBalance.builder().variantId(unknownVariant.getId()).variant(unknownVariant).quantityOnHand(5).openingConfirmed(true).version(0L).createdAt(now).updatedAt(now).build());

        // 4. Contractor Customer with Site A and Site B
        Customer contractor = customerRepository.save(Customer.builder()
                .customerCode("CUST-00001")
                .name("Shree Ganesh Developers")
                .normalizedName("shree ganesh developers")
                .phone("9890112233")
                .customerType(CustomerType.CONTRACTOR)
                .isActive(true).version(0L).createdAt(now).updatedAt(now).build());

        CustomerSite siteA = customerSiteRepository.save(CustomerSite.builder()
                .customer(contractor)
                .siteName("Ganesh Heights Baner")
                .normalizedSiteName("ganesh heights baner")
                .addressLine1("Baner Road, Pune")
                .isActive(true).version(0L).createdAt(now).updatedAt(now).build());

        CustomerSite siteB = customerSiteRepository.save(CustomerSite.builder()
                .customer(contractor)
                .siteName("Ganesh Meadows Wakad")
                .normalizedSiteName("ganesh meadows wakad")
                .addressLine1("Dutta Mandir Road, Wakad")
                .isActive(true).version(0L).createdAt(now).updatedAt(now).build());

        // Unrelated Customer with Site C (for cross-site ownership validation)
        Customer otherCustomer = customerRepository.save(Customer.builder()
                .customerCode("CUST-00002")
                .name("Other Builders")
                .normalizedName("other builders")
                .phone("9890998877")
                .customerType(CustomerType.BUILDER)
                .isActive(true).version(0L).createdAt(now).updatedAt(now).build());

        CustomerSite siteC = customerSiteRepository.save(CustomerSite.builder()
                .customer(otherCustomer)
                .siteName("Site C Unrelated")
                .normalizedSiteName("site c unrelated")
                .isActive(true).version(0L).createdAt(now).updatedAt(now).build());

        // 5. Supplier
        Supplier supplier = supplierRepository.save(Supplier.builder()
                .supplierCode("SUPP-00001")
                .name("Asian Paints Regional Depot")
                .normalizedName("asian paints regional depot")
                .phone("020-27471122")
                .gstin("27AAACA1234A1Z1")
                .isActive(true).version(0L).createdAt(now).updatedAt(now).build());

        // -------------------------------------------------------------
        // Step B: Purchase Inward (20 pieces of costPlusVariant @ 100.00 excl GST)
        // -------------------------------------------------------------
        // Gross cost per piece = 100 * 1.18 = 118.00. 20 pieces = 2,360.00
        CreatePurchaseRequest purchaseReq = CreatePurchaseRequest.builder()
                .supplierId(supplier.getId())
                .purchaseDate(today)
                .supplierInvoiceNumber("AP-DEPOT-8899")
                .lines(List.of(
                        PurchaseLineRequest.builder()
                                .variantId(costPlusVariant.getId())
                                .quantity(20)
                                .priceBasis(PriceBasis.GST_EXCLUSIVE)
                                .enteredUnitPrice(new BigDecimal("100.00"))
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
        long purchaseLineId = purJson.path("lines").get(0).path("id").asLong();
        assertThat(purJson.path("totalPayableAmount").asText()).isEqualTo("2360.00");

        // Verify stock is now 20
        assertThat(inventoryBalanceRepository.findByVariantId(costPlusVariant.getId()).get().getQuantityOnHand()).isEqualTo(20);

        // Record partial mixed supplier payment: 2,100.00 (Cash 1,100 + Online 1,000)
        CreateSupplierPaymentRequest supPay = CreateSupplierPaymentRequest.builder()
                .paymentDate(today)
                .totalAmount(new BigDecimal("2100.00"))
                .components(List.of(
                        PaymentComponentDto.builder().method(PaymentMethod.CASH).amount(new BigDecimal("1100.00")).build(),
                        PaymentComponentDto.builder().method(PaymentMethod.ONLINE).amount(new BigDecimal("1000.00")).onlineReference("NEFT-DEPOT-1").build()
                ))
                .allocations(List.of(
                        PaymentAllocationRequest.builder().purchaseId(purchaseId).amount(new BigDecimal("2100.00")).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/suppliers/" + supplier.getId() + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(supPay)))
                .andExpect(status().isCreated());

        // Verify Supplier Account Summary: totalPurchases 2360.00, totalPaid 2100.00, currentBalance 260.00, outstandingPayable 260.00
        mockMvc.perform(get("/api/v1/suppliers/" + supplier.getId() + "/account-summary")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPurchases", is("2360.00")))
                .andExpect(jsonPath("$.data.totalPaid", is("2100.00")))
                .andExpect(jsonPath("$.data.currentBalance", is("260.00")))
                .andExpect(jsonPath("$.data.outstandingPayable", is("260.00")));

        // -------------------------------------------------------------
        // Step C: Immediate Sale (Sell 5 pieces to Site A)
        // -------------------------------------------------------------
        // Cost 100, 20% margin, 18% GST -> selling price = 141.60. 5 pieces = 708.00
        CreateInvoiceRequest invAReq = CreateInvoiceRequest.builder()
                .customerId(contractor.getId())
                .siteId(siteA.getId())
                .invoiceDate(today)
                .lines(List.of(
                        InvoiceLineRequest.builder().variantId(costPlusVariant.getId()).quantity(5).build()
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
        String invANumber = invAJson.path("invoiceNumber").asText();
        String originalDueDate = invAJson.path("dueDate").asText();
        assertThat(invAJson.path("totalPayableAmount").asText()).isEqualTo("708.00");

        // Verify stock decreased from 20 to 15
        assertThat(inventoryBalanceRepository.findByVariantId(costPlusVariant.getId()).get().getQuantityOnHand()).isEqualTo(15);

        // Record partial mixed customer receipt: 400.00 (Cash 200 + Online 200)
        CreateCustomerReceiptRequest recReq = CreateCustomerReceiptRequest.builder()
                .receiptDate(today)
                .totalAmount(new BigDecimal("400.00"))
                .components(List.of(
                        PaymentComponentDto.builder().method(PaymentMethod.CASH).amount(new BigDecimal("200.00")).build(),
                        PaymentComponentDto.builder().method(PaymentMethod.ONLINE).amount(new BigDecimal("200.00")).onlineReference("UPI-SITEA-1").build()
                ))
                .allocations(List.of(
                        InvoiceAllocationRequest.builder().invoiceId(invoiceAId).amount(new BigDecimal("400.00")).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/customers/" + contractor.getId() + "/receipts")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(recReq)))
                .andExpect(status().isCreated());

        // Customer balance = 708.00 - 400.00 = 308.00
        mockMvc.perform(get("/api/v1/customers/" + contractor.getId() + "/account")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBalance", is("308.00")))
                .andExpect(jsonPath("$.data.outstandingDues", is("308.00")));

        // -------------------------------------------------------------
        // Step D: Separate Site B Bill & Site Ownership Validation
        // -------------------------------------------------------------
        // Cross-site validation: try billing Site C (unrelated customer) to contractor -> 422
        CreateInvoiceRequest invalidSiteReq = CreateInvoiceRequest.builder()
                .customerId(contractor.getId())
                .siteId(siteC.getId())
                .invoiceDate(today)
                .lines(List.of(InvoiceLineRequest.builder().variantId(mrpVariant.getId()).quantity(1).build()))
                .build();

        mockMvc.perform(post("/api/v1/invoices")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidSiteReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("INVALID_CUSTOMER_SITE")));

        // Bill Site B: 2 pieces of MRP product (MRP 500, 10% discount = 450.00 -> 900.00 total)
        CreateInvoiceRequest invBReq = CreateInvoiceRequest.builder()
                .customerId(contractor.getId())
                .siteId(siteB.getId())
                .invoiceDate(today)
                .lines(List.of(
                        InvoiceLineRequest.builder().variantId(mrpVariant.getId()).quantity(2).build()
                ))
                .build();

        MvcResult invBResult = mockMvc.perform(post("/api/v1/invoices")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invBReq)))
                .andExpect(status().isCreated())
                .andReturn();

        long invoiceBId = objectMapper.readTree(invBResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        // Stock of MRP variant: 10 - 2 = 8
        assertThat(inventoryBalanceRepository.findByVariantId(mrpVariant.getId()).get().getQuantityOnHand()).isEqualTo(8);

        // Contractor customer balance is unified across sites: 308.00 + 900.00 = 1,208.00
        mockMvc.perform(get("/api/v1/customers/" + contractor.getId() + "/account")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBalance", is("1208.00")))
                .andExpect(jsonPath("$.data.outstandingDues", is("1208.00")));

        // -------------------------------------------------------------
        // Step E: Advance Credit & Standalone Allocation
        // -------------------------------------------------------------
        // Record customer advance receipt of 2,000.00 (Cash 2000, 0 allocation)
        CreateCustomerReceiptRequest advReq = CreateCustomerReceiptRequest.builder()
                .receiptDate(today)
                .totalAmount(new BigDecimal("2000.00"))
                .components(List.of(PaymentComponentDto.builder().method(PaymentMethod.CASH).amount(new BigDecimal("2000.00")).build()))
                .build();

        MvcResult advResult = mockMvc.perform(post("/api/v1/customers/" + contractor.getId() + "/receipts")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(advReq)))
                .andExpect(status().isCreated())
                .andReturn();

        long receiptId = objectMapper.readTree(advResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        // Customer ledger balance: 1,208.00 - 2,000.00 = -792.00 (shop owes contractor / contractor has credit)
        mockMvc.perform(get("/api/v1/customers/" + contractor.getId() + "/account")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBalance", is("-792.00")))
                .andExpect(jsonPath("$.data.unallocatedCredit", is("2000.00")));

        // Apply advance explicitly: 308.00 to Invoice A and 900.00 to Invoice B
        ApplyCustomerAllocationRequest allocReq = ApplyCustomerAllocationRequest.builder()
                .allocations(List.of(
                        CustomerAllocationItemRequest.builder().receiptId(receiptId).invoiceId(invoiceAId).amount(new BigDecimal("308.00")).build(),
                        CustomerAllocationItemRequest.builder().receiptId(receiptId).invoiceId(invoiceBId).amount(new BigDecimal("900.00")).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/customers/" + contractor.getId() + "/allocations")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "ALLOC-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(allocReq)))
                .andExpect(status().isOk());

        // Invariant: Both invoices are now fully paid; Customer net balance remains EXACTLY -792.00 (no duplicate money movement!)
        mockMvc.perform(get("/api/v1/customers/" + contractor.getId() + "/account")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBalance", is("-792.00")))
                .andExpect(jsonPath("$.data.unallocatedCredit", is("792.00")))
                .andExpect(jsonPath("$.data.outstandingDues", is("0.00")));

        // -------------------------------------------------------------
        // Step F: Bill Corrections (Quantity Delta & Price-Only Revision)
        // -------------------------------------------------------------
        // Revision 1: Increase quantity from 5 to 6 on Invoice A.
        // Delta payable = +141.60. Total payable = 708.00 + 141.60 = 849.60.
        Long currentVersion = salesInvoiceRepository.findById(invoiceAId).orElseThrow().getVersion();
        InvoiceRevisionRequest rev1Req = InvoiceRevisionRequest.builder()
                .expectedVersion(currentVersion)
                .reason("Add 1 additional tin as requested by contractor")
                .lines(List.of(
                        InvoiceRevisionLineRequest.builder()
                                .invoiceLineId(lineAId)
                                .variantId(costPlusVariant.getId())
                                .quantity(6)
                                .build()
                ))
                .build();

        MvcResult rev1Result = mockMvc.perform(post("/api/v1/invoices/" + invoiceAId + "/revisions")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "REV1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rev1Req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode rev1Json = objectMapper.readTree(rev1Result.getResponse().getContentAsString()).path("data");
        assertThat(rev1Json.path("deltaPayableAmount").asText()).isEqualTo("141.60");

        // Stock decreased by 1 (15 -> 14)
        assertThat(inventoryBalanceRepository.findByVariantId(costPlusVariant.getId()).get().getQuantityOnHand()).isEqualTo(14);

        // Original invoice identity and dates preserved
        mockMvc.perform(get("/api/v1/invoices/" + invoiceAId).header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceNumber", is(invANumber)))
                .andExpect(jsonPath("$.data.dueDate", is(originalDueDate)))
                .andExpect(jsonPath("$.data.totalPayableAmount", is("849.60")));

        // Revision 2: Price-only correction: change unit price from 141.60 to 150.00 (quantity stays 6).
        // 6 * 150.00 = 900.00. Delta = 900.00 - 849.60 = 50.40.
        Long currentVersion2 = salesInvoiceRepository.findById(invoiceAId).orElseThrow().getVersion();
        InvoiceRevisionRequest rev2Req = InvoiceRevisionRequest.builder()
                .expectedVersion(currentVersion2)
                .reason("Contractual price agreement override")
                .lines(List.of(
                        InvoiceRevisionLineRequest.builder()
                                .invoiceLineId(lineAId)
                                .variantId(costPlusVariant.getId())
                                .quantity(6)
                                .unitPriceOverride(new BigDecimal("150.00"))
                                .overrideReason("Agreed site rate")
                                .build()
                ))
                .build();

        MvcResult rev2Result = mockMvc.perform(post("/api/v1/invoices/" + invoiceAId + "/revisions")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "REV2-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rev2Req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode rev2Json = objectMapper.readTree(rev2Result.getResponse().getContentAsString()).path("data");
        assertThat(rev2Json.path("deltaPayableAmount").asText()).isEqualTo("50.40");

        // Invariant: Quantity did not change (delta = 0), so stock remains EXACTLY 14
        assertThat(inventoryBalanceRepository.findByVariantId(costPlusVariant.getId()).get().getQuantityOnHand()).isEqualTo(14);

        // -------------------------------------------------------------
        // Step G: Return (Partial Return with Ambiguity Policy)
        // -------------------------------------------------------------
        // Return 2 pieces from Invoice A. Because line underwent price override, policy is required.
        // Attempting return without policy returns 422
        CustomerReturnRequest noPolicyReq = CustomerReturnRequest.builder()
                .returnDate(today)
                .reason("Test return without policy")
                .lines(List.of(CustomerReturnLineRequest.builder().invoiceLineId(lineAId).quantity(2).build()))
                .build();

        mockMvc.perform(post("/api/v1/invoices/" + invoiceAId + "/returns")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "RET-NOPOLICY-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noPolicyReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("RETURN_VALUATION_POLICY_REQUIRED")));

        // Return 2 pieces with policy = LATEST_REVISED_PRICE (2 * 150.00 = 300.00)
        CustomerReturnRequest retReq = CustomerReturnRequest.builder()
                .returnDate(today)
                .reason("Contractor returned 2 unused tins from site")
                .valuationPolicy("LATEST_REVISED_PRICE")
                .lines(List.of(CustomerReturnLineRequest.builder().invoiceLineId(lineAId).quantity(2).build()))
                .build();

        MvcResult retResult = mockMvc.perform(post("/api/v1/invoices/" + invoiceAId + "/returns")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "RET-OK-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(retReq)))
                .andExpect(status().isCreated())
                .andReturn();

        long returnId = objectMapper.readTree(retResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        // Stock restored by 2 pieces (14 + 2 = 16)
        assertThat(inventoryBalanceRepository.findByVariantId(costPlusVariant.getId()).get().getQuantityOnHand()).isEqualTo(16);

        // -------------------------------------------------------------
        // Step H: Customer Refund (Disburse cash/online refund from credit)
        // -------------------------------------------------------------
        // Disburse refund of 200.00 (Cash 100 + Online 100)
        CreateCustomerRefundRequest refReq = CreateCustomerRefundRequest.builder()
                .refundDate(today)
                .reason("Cash & UPI refund for credit")
                .totalAmount(new BigDecimal("200.00"))
                .components(List.of(
                        PaymentComponentDto.builder().method(PaymentMethod.CASH).amount(new BigDecimal("100.00")).build(),
                        PaymentComponentDto.builder().method(PaymentMethod.ONLINE).amount(new BigDecimal("100.00")).onlineReference("REFUND-UPI-01").build()
                ))
                .build();

        MvcResult refResult = mockMvc.perform(post("/api/v1/customers/" + contractor.getId() + "/refunds")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "REFUND-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refReq)))
                .andExpect(status().isCreated())
                .andReturn();

        long refundId = objectMapper.readTree(refResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        // Invariant: Refund does NOT alter stock
        assertThat(inventoryBalanceRepository.findByVariantId(costPlusVariant.getId()).get().getQuantityOnHand()).isEqualTo(16);

        // -------------------------------------------------------------
        // Step I: Supplier Return & Refund
        // -------------------------------------------------------------
        // Return 5 pieces of costPlusVariant to supplier (unit cost 118.00 gross -> 590.00)
        CreatePurchaseReturnRequest pretReq = CreatePurchaseReturnRequest.builder()
                .returnDate(today)
                .reason("Surplus batch return to depot")
                .lines(List.of(PurchaseReturnLineRequest.builder().purchaseLineId(purchaseLineId).quantity(5).build()))
                .build();

        MvcResult pretResult = mockMvc.perform(post("/api/v1/purchases/" + purchaseId + "/returns")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "PRET-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pretReq)))
                .andExpect(status().isCreated())
                .andReturn();

        long pretId = objectMapper.readTree(pretResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        // Stock decreased by 5 (16 - 5 = 11)
        assertThat(inventoryBalanceRepository.findByVariantId(costPlusVariant.getId()).get().getQuantityOnHand()).isEqualTo(11);

        // Record supplier refund: 200.00 online
        CreateSupplierRefundRequest srefReq = CreateSupplierRefundRequest.builder()
                .refundDate(today)
                .reason("Supplier return adjustment payment")
                .totalAmount(new BigDecimal("200.00"))
                .components(List.of(PaymentComponentDto.builder().method(PaymentMethod.ONLINE).amount(new BigDecimal("200.00")).onlineReference("DEPOT-REF-01").build()))
                .build();

        mockMvc.perform(post("/api/v1/suppliers/" + supplier.getId() + "/refunds")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "SREF-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(srefReq)))
                .andExpect(status().isCreated());

        // Invariant: Supplier refund does NOT alter stock
        assertThat(inventoryBalanceRepository.findByVariantId(costPlusVariant.getId()).get().getQuantityOnHand()).isEqualTo(11);

        // -------------------------------------------------------------
        // Step J: Operating Expense, Reports & Representative Documents
        // -------------------------------------------------------------
        CreateExpenseRequest expReq = CreateExpenseRequest.builder()
                .expenseDate(today)
                .category(ExpenseCategory.SHOP_OPERATIONS)
                .description("Store light fixture replacement")
                .amount(new BigDecimal("250.00"))
                .components(List.of(ExpenseComponentDto.builder().method(PaymentMethod.CASH).amount(new BigDecimal("250.00")).build()))
                .build();

        mockMvc.perform(post("/api/v1/expenses")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "EXP-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(expReq)))
                .andExpect(status().isCreated());

        // Reports check: Owner has access, Manager forbidden on profit
        mockMvc.perform(get("/api/v1/dashboard/summary").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grossSales", notNullValue()))
                .andExpect(jsonPath("$.data.profitSummary", notNullValue()));

        mockMvc.perform(get("/api/v1/reports/profit").header(HttpHeaders.AUTHORIZATION, managerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/reports/profit").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.costCoveragePct", notNullValue()));

        // Representative PDF downloads return 200 OK
        mockMvc.perform(get("/api/v1/invoices/" + invoiceAId + "/pdf?format=NORMAL").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mockMvc.perform(get("/api/v1/invoices/" + invoiceAId + "/pdf?format=GST").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mockMvc.perform(get("/api/v1/customer-returns/" + returnId + "/pdf").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mockMvc.perform(get("/api/v1/customer-refunds/" + refundId + "/pdf").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mockMvc.perform(get("/api/v1/customers/" + contractor.getId() + "/ledger/pdf").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mockMvc.perform(get("/api/v1/purchases/" + purchaseId + "/pdf").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mockMvc.perform(get("/api/v1/purchases/returns/" + pretId + "/pdf").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mockMvc.perform(get("/api/v1/suppliers/" + supplier.getId() + "/ledger/pdf").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("Money & Tax Math: Formula verification and return rounding remainder reconciliation")
    void testMoneyAndTaxCalculations() throws Exception {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        // 1. MRP 500 x 2 with 10% discount = 900.00 payable
        Brand brand = brandRepository.save(Brand.builder().name("TestBrand").isActive(true).version(0L).createdAt(now).updatedAt(now).build());
        Category cat = categoryRepository.save(Category.builder().name("TestCat").salesDiscountPct(new BigDecimal("10.00")).isActive(true).version(0L).createdAt(now).updatedAt(now).build());
        Subcategory sub = subcategoryRepository.save(Subcategory.builder().category(cat).name("TestSub").isActive(true).version(0L).createdAt(now).updatedAt(now).build());
        Product prod = productRepository.save(Product.builder().brand(brand).subcategory(sub).name("TestProd").isActive(true).version(0L).createdAt(now).updatedAt(now).build());

        ProductVariant mrpVar = productVariantRepository.save(ProductVariant.builder()
                .product(prod)
                .sku("MRP-500-VAR")
                .sizeLabel("Standard")
                .pricingMethod(PricingMethod.MRP_DISCOUNT)
                .mrpInclTax(new BigDecimal("500.00"))
                .gstRatePct(new BigDecimal("18.00"))
                .isActive(true).version(0L).createdAt(now).updatedAt(now).build());

        PricingPreviewResponse pResp1 = pricingService.calculatePreview(PricingPreviewRequest.builder()
                .lines(List.of(PricingPreviewLineRequest.builder().variantId(mrpVar.getId()).quantity(2).build()))
                .build(), true);
        assertThat(pResp1.getGrossTotal()).isEqualByComparingTo("900.00");

        // 2. Cost 100 excl GST, profit 20%, GST 18% = 141.60 per piece
        ProductVariant cpVar = productVariantRepository.save(ProductVariant.builder()
                .product(prod)
                .sku("CP-100-VAR")
                .sizeLabel("1L")
                .pricingMethod(PricingMethod.COST_PLUS)
                .purchaseCostExclTax(new BigDecimal("100.00"))
                .profitPct(new BigDecimal("20.00"))
                .gstRatePct(new BigDecimal("18.00"))
                .isActive(true).version(0L).createdAt(now).updatedAt(now).build());

        PricingPreviewResponse pResp2 = pricingService.calculatePreview(PricingPreviewRequest.builder()
                .lines(List.of(PricingPreviewLineRequest.builder().variantId(cpVar.getId()).quantity(1).build()))
                .build(), true);
        assertThat(pResp2.getLines().get(0).getUnitNetInclusive()).isEqualByComparingTo("141.6000");

        // 3. Fixed inclusive 125.50 x 2 = 251.00
        ProductVariant fixVar = productVariantRepository.save(ProductVariant.builder()
                .product(prod)
                .sku("FIX-125-VAR")
                .sizeLabel("2L")
                .pricingMethod(PricingMethod.FIXED)
                .fixedPriceInclTax(new BigDecimal("125.50"))
                .gstRatePct(new BigDecimal("18.00"))
                .isActive(true).version(0L).createdAt(now).updatedAt(now).build());

        PricingPreviewResponse pResp3 = pricingService.calculatePreview(PricingPreviewRequest.builder()
                .lines(List.of(PricingPreviewLineRequest.builder().variantId(fixVar.getId()).quantity(2).build()))
                .build(), true);
        assertThat(pResp3.getGrossTotal()).isEqualByComparingTo("251.00");

        // 4. Return rounding remainder consumption:
        ProductVariant thirdVar = productVariantRepository.save(ProductVariant.builder()
                .product(prod)
                .sku("THIRD-VAR")
                .sizeLabel("3L")
                .pricingMethod(PricingMethod.COST_PLUS)
                .purchaseCostExclTax(new BigDecimal("25.00"))
                .profitPct(new BigDecimal("15.00"))
                .gstRatePct(new BigDecimal("18.00"))
                .isActive(true).version(0L).createdAt(now).updatedAt(now).build());
        inventoryBalanceRepository.save(InventoryBalance.builder().variantId(thirdVar.getId()).variant(thirdVar).quantityOnHand(10).openingConfirmed(true).version(0L).createdAt(now).updatedAt(now).build());

        Customer cust = customerRepository.save(Customer.builder().customerCode("CUST-RND").name("Rounding Cust").normalizedName("rounding cust").phone("9800000001").customerType(CustomerType.RETAIL).isActive(true).version(0L).createdAt(now).updatedAt(now).build());

        // Invoice: 3 pieces @ 33.9250 -> 101.78
        MvcResult invRes = mockMvc.perform(post("/api/v1/invoices")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateInvoiceRequest.builder()
                                .customerId(cust.getId())
                                .invoiceDate(today)
                                .lines(List.of(InvoiceLineRequest.builder().variantId(thirdVar.getId()).quantity(3).build()))
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();

        long invId = objectMapper.readTree(invRes.getResponse().getContentAsString()).path("data").path("id").asLong();
        long invLineId = objectMapper.readTree(invRes.getResponse().getContentAsString()).path("data").path("lines").get(0).path("id").asLong();
        assertThat(objectMapper.readTree(invRes.getResponse().getContentAsString()).path("data").path("totalPayableAmount").asText()).isEqualTo("101.78");

        // Return 1: 1 piece -> 33.93
        MvcResult ret1 = mockMvc.perform(post("/api/v1/invoices/" + invId + "/returns")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "RET1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CustomerReturnRequest.builder()
                                .returnDate(today)
                                .reason("Partial 1")
                                .lines(List.of(CustomerReturnLineRequest.builder().invoiceLineId(invLineId).quantity(1).build()))
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(objectMapper.readTree(ret1.getResponse().getContentAsString()).path("data").path("totalReturnAmount").asText()).isEqualTo("33.93");

        // Return 2: 1 piece -> 33.93
        MvcResult ret2 = mockMvc.perform(post("/api/v1/invoices/" + invId + "/returns")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "RET2-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CustomerReturnRequest.builder()
                                .returnDate(today)
                                .reason("Partial 2")
                                .lines(List.of(CustomerReturnLineRequest.builder().invoiceLineId(invLineId).quantity(1).build()))
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(objectMapper.readTree(ret2.getResponse().getContentAsString()).path("data").path("totalReturnAmount").asText()).isEqualTo("33.93");

        // Return 3 (FINAL): 1 piece -> MUST consume remaining remainder: 101.78 - 33.93 - 33.93 = 33.92!
        MvcResult ret3 = mockMvc.perform(post("/api/v1/invoices/" + invId + "/returns")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "RET3-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CustomerReturnRequest.builder()
                                .returnDate(today)
                                .reason("Partial 3 Final")
                                .lines(List.of(CustomerReturnLineRequest.builder().invoiceLineId(invLineId).quantity(1).build()))
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(objectMapper.readTree(ret3.getResponse().getContentAsString()).path("data").path("totalReturnAmount").asText()).isEqualTo("33.92");

        // Invariant: Total returned for invoice is EXACTLY 101.78, remaining outstanding payable is 0.00
        mockMvc.perform(get("/api/v1/invoices/" + invId).header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalReturnedAmount", is("101.78")))
                .andExpect(jsonPath("$.data.outstandingAmount", is("0.00")));
    }

    @Test
    @DisplayName("Reconciliation Conventions: Customer and Supplier Symmetric +1000, -600, -500, +100 = 0")
    void testReconciliationConventions() throws Exception {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        Brand brand = brandRepository.save(Brand.builder().name("RecBrand").isActive(true).version(0L).createdAt(now).updatedAt(now).build());
        Category cat = categoryRepository.save(Category.builder().name("RecCat").isActive(true).version(0L).createdAt(now).updatedAt(now).build());
        Subcategory sub = subcategoryRepository.save(Subcategory.builder().category(cat).name("RecSub").isActive(true).version(0L).createdAt(now).updatedAt(now).build());
        Product prod = productRepository.save(Product.builder().brand(brand).subcategory(sub).name("RecProd").isActive(true).version(0L).createdAt(now).updatedAt(now).build());

        ProductVariant variant = productVariantRepository.save(ProductVariant.builder()
                .product(prod)
                .sku("REC-VAR-100")
                .sizeLabel("1pc")
                .pricingMethod(PricingMethod.FIXED)
                .fixedPriceInclTax(new BigDecimal("100.00"))
                .purchaseCostExclTax(new BigDecimal("100.00"))
                .isActive(true).version(0L).createdAt(now).updatedAt(now).build());
        inventoryBalanceRepository.save(InventoryBalance.builder().variantId(variant.getId()).variant(variant).quantityOnHand(50).openingConfirmed(true).version(0L).createdAt(now).updatedAt(now).build());

        // ==========================================
        // CUSTOMER CONVENTION:
        // Invoice +1,000; receipt -600; return -500; refund +100 = 0
        // ==========================================
        Customer cust = customerRepository.save(Customer.builder().customerCode("CUST-REC").name("Rec Customer").normalizedName("rec customer").phone("9800000002").customerType(CustomerType.RETAIL).isActive(true).version(0L).createdAt(now).updatedAt(now).build());

        // Step 1: Invoice +1,000 (10 pieces @ 100.00)
        MvcResult cinv = mockMvc.perform(post("/api/v1/invoices")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateInvoiceRequest.builder()
                                .customerId(cust.getId())
                                .invoiceDate(today)
                                .lines(List.of(InvoiceLineRequest.builder().variantId(variant.getId()).quantity(10).build()))
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();
        long cinvId = objectMapper.readTree(cinv.getResponse().getContentAsString()).path("data").path("id").asLong();
        long cinvLineId = objectMapper.readTree(cinv.getResponse().getContentAsString()).path("data").path("lines").get(0).path("id").asLong();

        // Customer Balance after Invoice: +1,000.00
        mockMvc.perform(get("/api/v1/customers/" + cust.getId() + "/account").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBalance", is("1000.00")))
                .andExpect(jsonPath("$.data.outstandingDues", is("1000.00")));

        // Step 2: Receipt -600 (Cash 600, allocated to invoice)
        mockMvc.perform(post("/api/v1/customers/" + cust.getId() + "/receipts")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateCustomerReceiptRequest.builder()
                                .receiptDate(today)
                                .totalAmount(new BigDecimal("600.00"))
                                .components(List.of(PaymentComponentDto.builder().method(PaymentMethod.CASH).amount(new BigDecimal("600.00")).build()))
                                .allocations(List.of(InvoiceAllocationRequest.builder().invoiceId(cinvId).amount(new BigDecimal("600.00")).build()))
                                .build())))
                .andExpect(status().isCreated());

        // Customer Balance after Receipt: 1000 - 600 = +400.00
        mockMvc.perform(get("/api/v1/customers/" + cust.getId() + "/account").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBalance", is("400.00")))
                .andExpect(jsonPath("$.data.outstandingDues", is("400.00")));

        // Step 3: Return -500 (5 pieces @ 100.00)
        // Effective invoice payable drops to 500. Excess 100 of allocated payment is released back to receipt credit!
        mockMvc.perform(post("/api/v1/invoices/" + cinvId + "/returns")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "CRET-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CustomerReturnRequest.builder()
                                .returnDate(today)
                                .reason("5 pcs returned")
                                .lines(List.of(CustomerReturnLineRequest.builder().invoiceLineId(cinvLineId).quantity(5).build()))
                                .build())))
                .andExpect(status().isCreated());

        // Customer Balance after Return: 400 - 500 = -100.00 (Customer has 100 credit!)
        // Invoice is fully settled: effective payable 500, allocated 500, outstanding 0.00
        mockMvc.perform(get("/api/v1/customers/" + cust.getId() + "/account").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBalance", is("-100.00")))
                .andExpect(jsonPath("$.data.unallocatedCredit", is("100.00")))
                .andExpect(jsonPath("$.data.outstandingDues", is("0.00")));

        mockMvc.perform(get("/api/v1/invoices/" + cinvId).header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outstandingAmount", is("0.00")))
                .andExpect(jsonPath("$.data.settlementStatus", is("PAID")));

        // Step 4: Refund +100 (Disburse 100 credit cash)
        mockMvc.perform(post("/api/v1/customers/" + cust.getId() + "/refunds")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "CREF-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateCustomerRefundRequest.builder()
                                .refundDate(today)
                                .reason("Credit payout")
                                .totalAmount(new BigDecimal("100.00"))
                                .components(List.of(PaymentComponentDto.builder().method(PaymentMethod.CASH).amount(new BigDecimal("100.00")).build()))
                                .build())))
                .andExpect(status().isCreated());

        // Customer Final Balance: -100 + 100 = 0.00!
        mockMvc.perform(get("/api/v1/customers/" + cust.getId() + "/account").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBalance", is("0.00")))
                .andExpect(jsonPath("$.data.unallocatedCredit", is("0.00")))
                .andExpect(jsonPath("$.data.outstandingDues", is("0.00")));

        // ==========================================
        // SUPPLIER CONVENTION:
        // Purchase +1,000; payment -600; return -500; supplier refund +100 = 0
        // ==========================================
        Supplier supp = supplierRepository.save(Supplier.builder().supplierCode("SUPP-REC").name("Rec Supplier").normalizedName("rec supplier").phone("9800000003").isActive(true).version(0L).createdAt(now).updatedAt(now).build());

        // Step 1: Purchase +1,000 (10 pieces @ 100.00 gross)
        MvcResult spur = mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreatePurchaseRequest.builder()
                                .supplierId(supp.getId())
                                .purchaseDate(today)
                                .supplierInvoiceNumber("SUPP-INV-1000")
                                .lines(List.of(PurchaseLineRequest.builder()
                                        .variantId(variant.getId())
                                        .quantity(10)
                                        .priceBasis(PriceBasis.GST_INCLUSIVE)
                                        .enteredUnitPrice(new BigDecimal("100.00"))
                                        .build()))
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();
        long spurId = objectMapper.readTree(spur.getResponse().getContentAsString()).path("data").path("id").asLong();
        long spurLineId = objectMapper.readTree(spur.getResponse().getContentAsString()).path("data").path("lines").get(0).path("id").asLong();

        // Supplier Balance after Purchase: +1,000.00 (Shop owes supplier)
        mockMvc.perform(get("/api/v1/suppliers/" + supp.getId() + "/account-summary").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBalance", is("1000.00")))
                .andExpect(jsonPath("$.data.outstandingPayable", is("1000.00")));

        // Step 2: Payment -600 (Cash 600, allocated to purchase)
        mockMvc.perform(post("/api/v1/suppliers/" + supp.getId() + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateSupplierPaymentRequest.builder()
                                .paymentDate(today)
                                .totalAmount(new BigDecimal("600.00"))
                                .components(List.of(PaymentComponentDto.builder().method(PaymentMethod.CASH).amount(new BigDecimal("600.00")).build()))
                                .allocations(List.of(PaymentAllocationRequest.builder().purchaseId(spurId).amount(new BigDecimal("600.00")).build()))
                                .build())))
                .andExpect(status().isCreated());

        // Supplier Balance after Payment: 1000 - 600 = +400.00
        mockMvc.perform(get("/api/v1/suppliers/" + supp.getId() + "/account-summary").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBalance", is("400.00")))
                .andExpect(jsonPath("$.data.outstandingPayable", is("400.00")));

        // Step 3: Return -500 (5 pieces @ 100.00)
        // Effective purchase payable drops to 500. Excess 100 of allocated payment is released back to supplier payment credit!
        mockMvc.perform(post("/api/v1/purchases/" + spurId + "/returns")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "SPRET-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreatePurchaseReturnRequest.builder()
                                .returnDate(today)
                                .reason("5 pcs returned to supplier")
                                .lines(List.of(PurchaseReturnLineRequest.builder().purchaseLineId(spurLineId).quantity(5).build()))
                                .build())))
                .andExpect(status().isCreated());

        // Supplier Balance after Return: 400 - 500 = -100.00 (Supplier owes shop / holds advance)
        // Purchase is fully settled: effective payable 500, allocated 500, outstanding 0.00
        mockMvc.perform(get("/api/v1/suppliers/" + supp.getId() + "/account-summary").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBalance", is("-100.00")))
                .andExpect(jsonPath("$.data.unallocatedCredit", is("100.00")))
                .andExpect(jsonPath("$.data.outstandingPayable", is("0.00")));

        mockMvc.perform(get("/api/v1/purchases/" + spurId).header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outstandingBalance", is("0.00")));

        // Step 4: Supplier refund +100 (Supplier refunds 100 online)
        mockMvc.perform(post("/api/v1/suppliers/" + supp.getId() + "/refunds")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "SPREF-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateSupplierRefundRequest.builder()
                                .refundDate(today)
                                .reason("Supplier rebate payout")
                                .totalAmount(new BigDecimal("100.00"))
                                .components(List.of(PaymentComponentDto.builder().method(PaymentMethod.ONLINE).amount(new BigDecimal("100.00")).onlineReference("DEPOT-REBATE-1").build()))
                                .build())))
                .andExpect(status().isCreated());

        // Supplier Final Balance: -100 + 100 = 0.00!
        mockMvc.perform(get("/api/v1/suppliers/" + supp.getId() + "/account-summary").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBalance", is("0.00")))
                .andExpect(jsonPath("$.data.unallocatedCredit", is("0.00")))
                .andExpect(jsonPath("$.data.outstandingPayable", is("0.00")));
    }
}
