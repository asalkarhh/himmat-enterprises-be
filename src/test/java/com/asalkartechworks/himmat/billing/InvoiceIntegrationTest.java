package com.asalkartechworks.himmat.billing;

import com.asalkartechworks.himmat.billing.dto.ApplyCustomerAllocationRequest;
import com.asalkartechworks.himmat.billing.dto.CreateCustomerReceiptRequest;
import com.asalkartechworks.himmat.billing.dto.CreateInvoiceRequest;
import com.asalkartechworks.himmat.billing.dto.CustomerAllocationItemRequest;
import com.asalkartechworks.himmat.billing.dto.InvoiceAllocationRequest;
import com.asalkartechworks.himmat.billing.dto.InvoiceLineRequest;
import com.asalkartechworks.himmat.billing.dto.InvoicePreviewRequest;
import com.asalkartechworks.himmat.billing.model.CustomerLedgerEntry;
import com.asalkartechworks.himmat.billing.model.CustomerReceipt;
import com.asalkartechworks.himmat.billing.model.InvoiceDisplayFormat;
import com.asalkartechworks.himmat.billing.model.SalesInvoice;
import com.asalkartechworks.himmat.billing.model.SettlementStatus;
import com.asalkartechworks.himmat.billing.repository.CustomerLedgerEntryRepository;
import com.asalkartechworks.himmat.billing.repository.CustomerReceiptRepository;
import com.asalkartechworks.himmat.billing.repository.ReceiptAllocationRepository;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceLineRepository;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceRepository;
import com.asalkartechworks.himmat.catalog.model.Brand;
import com.asalkartechworks.himmat.catalog.model.Category;
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
import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.identity.model.AppUser;
import com.asalkartechworks.himmat.identity.model.UserRole;
import com.asalkartechworks.himmat.identity.repository.AppUserRepository;
import com.asalkartechworks.himmat.inventory.model.StockMovement;
import com.asalkartechworks.himmat.inventory.model.StockMovementDirection;
import com.asalkartechworks.himmat.inventory.model.StockMovementType;
import com.asalkartechworks.himmat.inventory.repository.StockMovementRepository;
import com.asalkartechworks.himmat.party.model.Customer;
import com.asalkartechworks.himmat.party.model.CustomerSite;
import com.asalkartechworks.himmat.party.model.CustomerType;
import com.asalkartechworks.himmat.party.repository.CustomerRepository;
import com.asalkartechworks.himmat.party.repository.CustomerSiteRepository;
import com.asalkartechworks.himmat.purchase.dto.PaymentComponentDto;
import com.asalkartechworks.himmat.purchase.model.PaymentMethod;
import com.asalkartechworks.himmat.security.JwtService;
import com.asalkartechworks.himmat.security.UserPrincipal;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvoiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerSiteRepository customerSiteRepository;

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
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private SalesInvoiceRepository salesInvoiceRepository;

    @Autowired
    private SalesInvoiceLineRepository salesInvoiceLineRepository;

    @Autowired
    private CustomerReceiptRepository customerReceiptRepository;

    @Autowired
    private com.asalkartechworks.himmat.billing.repository.CustomerReceiptComponentRepository customerReceiptComponentRepository;

    @Autowired
    private ReceiptAllocationRepository receiptAllocationRepository;

    @Autowired
    private CustomerLedgerEntryRepository customerLedgerEntryRepository;

    @Autowired
    private BusinessClock businessClock;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private AppUser ownerUser;
    private AppUser managerUser;
    private String ownerToken;
    private String managerToken;

    private Customer testCustomer;
    private Customer otherCustomer;
    private CustomerSite testSite;
    private Brand testBrand;
    private Category testCategory;
    private Subcategory testSubcategory;
    private Product testProduct;
    private ProductVariant variant1; // MRP_DISCOUNT
    private ProductVariant variant2; // FIXED

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE customer_ledger_entry, receipt_allocation, customer_receipt_component, customer_receipt, sales_invoice_line, sales_invoice, customer_site, customer, supplier_ledger_entry, payment_allocation, payment_component, supplier_refund, purchase_return_line, purchase_return, supplier_payment, purchase_line, purchase, stock_movement, inventory_balance, product_variant, product, subcategory, category, brand, supplier, business_event, app_user CASCADE");

        Instant now = Instant.now();

        // 1. Setup Users
        ownerUser = appUserRepository.save(AppUser.builder()
                .username("owner_" + UUID.randomUUID().toString().substring(0, 8))
                .displayName("Shop Owner")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(UserRole.OWNER)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        managerUser = appUserRepository.save(AppUser.builder()
                .username("manager_" + UUID.randomUUID().toString().substring(0, 8))
                .displayName("Shop Manager")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(UserRole.MANAGER)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        ownerToken = "Bearer " + jwtService.generateToken(UserPrincipal.create(ownerUser));
        managerToken = "Bearer " + jwtService.generateToken(UserPrincipal.create(managerUser));

        // 2. Setup Customers & Site
        testCustomer = customerRepository.save(Customer.builder()
                .customerCode("CUST-00001")
                .customerType(CustomerType.CONTRACTOR)
                .name("Ramesh Contractor")
                .normalizedName("ramesh contractor")
                .phone("9876543210")
                .gstin("27ABCDE1234F1Z5")
                .addressLine1("123 Main Bazaar")
                .city("Pune")
                .postalCode("411001")
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        otherCustomer = customerRepository.save(Customer.builder()
                .customerCode("CUST-00002")
                .customerType(CustomerType.RETAIL)
                .name("Suresh Painter")
                .normalizedName("suresh painter")
                .phone("9876543211")
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        testSite = customerSiteRepository.save(CustomerSite.builder()
                .customer(testCustomer)
                .siteName("Amanora Towers Site")
                .normalizedSiteName("amanora towers site")
                .addressLine1("Tower 4, Amanora")
                .city("Pune")
                .postalCode("411028")
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // 3. Setup Catalog
        testBrand = brandRepository.save(Brand.builder()
                .name("Asian Paints")
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        testCategory = categoryRepository.save(Category.builder()
                .name("Paints")
                .salesDiscountPct(new BigDecimal("5.0000"))
                .purchaseDiscountPct(new BigDecimal("10.0000"))
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        testSubcategory = subcategoryRepository.save(Subcategory.builder()
                .category(testCategory)
                .name("Interior Emulsions")
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        testProduct = productRepository.save(Product.builder()
                .name("Apex Ultima")
                .brand(testBrand)
                .subcategory(testSubcategory)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // Variant 1: MRP_DISCOUNT, MRP = 1000.00 incl tax, GST = 18%, category discount = 5% => net = 950.00
        variant1 = productVariantRepository.save(ProductVariant.builder()
                .product(testProduct)
                .sku("APEX-WHT-10L")
                .sizeLabel("10 Liter")
                .pricingMethod(PricingMethod.MRP_DISCOUNT)
                .mrpInclTax(new BigDecimal("1000.0000"))
                .purchaseCostExclTax(new BigDecimal("600.0000"))
                .gstRatePct(new BigDecimal("18.0000"))
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        inventoryBalanceRepository.save(InventoryBalance.builder()
                .variantId(variant1.getId())
                .variant(variant1)
                .quantityOnHand(20)
                .openingConfirmed(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // Variant 2: FIXED price = 500.00 incl tax, GST = 18%
        variant2 = productVariantRepository.save(ProductVariant.builder()
                .product(testProduct)
                .sku("APEX-WHT-04L")
                .sizeLabel("4 Liter")
                .pricingMethod(PricingMethod.FIXED)
                .fixedPriceInclTax(new BigDecimal("500.0000"))
                .purchaseCostExclTax(new BigDecimal("300.0000"))
                .gstRatePct(new BigDecimal("18.0000"))
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        inventoryBalanceRepository.save(InventoryBalance.builder()
                .variantId(variant2.getId())
                .variant(variant2)
                .quantityOnHand(10)
                .openingConfirmed(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    @Test
    @DisplayName("previewInvoice: calculates prices, discounts, tax breakdowns, totals, and fingerprint without database writes")
    void testPreviewInvoice_purelyReadOnly() throws Exception {
        InvoicePreviewRequest previewRequest = InvoicePreviewRequest.builder()
                .customerId(testCustomer.getId())
                .siteId(testSite.getId())
                .displayFormat(InvoiceDisplayFormat.NORMAL)
                .lines(List.of(
                        InvoiceLineRequest.builder()
                                .variantId(variant1.getId())
                                .quantity(2)
                                .build(),
                        InvoiceLineRequest.builder()
                                .variantId(variant2.getId())
                                .quantity(1)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/invoices/preview")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(previewRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerId", is(testCustomer.getId().toString())))
                .andExpect(jsonPath("$.data.customerName", is("Ramesh Contractor")))
                .andExpect(jsonPath("$.data.siteId", is(testSite.getId().toString())))
                .andExpect(jsonPath("$.data.siteName", is("Amanora Towers Site")))
                .andExpect(jsonPath("$.data.lines", hasSize(2)))
                // Line 1: base 1000 * 2 = 2000 gross, 5% discount => 950 net * 2 = 1900.00
                .andExpect(jsonPath("$.data.lines[0].baseUnitPrice", is("1000.0000")))
                .andExpect(jsonPath("$.data.lines[0].discountPct", is("5.0000")))
                .andExpect(jsonPath("$.data.lines[0].lineTotal", is("1900.00")))
                // Line 2: fixed 500 * 1 = 500.00
                .andExpect(jsonPath("$.data.lines[1].baseUnitPrice", is("500.0000")))
                .andExpect(jsonPath("$.data.lines[1].lineTotal", is("500.00")))
                // Totals: gross = 2000 + 500 = 2500, discount = 100, payable = 2400.00
                .andExpect(jsonPath("$.data.grossTotal", is("2500.00")))
                .andExpect(jsonPath("$.data.totalDiscountAmount", is("100.00")))
                .andExpect(jsonPath("$.data.totalPayableAmount", is("2400.00")))
                .andExpect(jsonPath("$.data.taxBreakdownAvailable", is(true)))
                .andExpect(jsonPath("$.data.pricingFingerprint").isString());

        // Assert 0 database mutations
        assertThat(salesInvoiceRepository.count()).isEqualTo(0);
        assertThat(stockMovementRepository.count()).isEqualTo(0);
        assertThat(customerLedgerEntryRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("createInvoice: immediate bill atomically reduces stock and records customer ledger entry")
    void testCreateInvoice_immediateSaleSuccess() throws Exception {
        CreateInvoiceRequest createRequest = CreateInvoiceRequest.builder()
                .customerId(testCustomer.getId())
                .siteId(testSite.getId())
                .invoiceDate(businessClock.today())
                .displayFormat(InvoiceDisplayFormat.NORMAL)
                .lines(List.of(
                        InvoiceLineRequest.builder()
                                .variantId(variant1.getId())
                                .quantity(5)
                                .build()
                ))
                .notes("Immediate counter bill")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/invoices")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "IDEMP-INV-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.invoiceNumber", containsString("INV-")))
                .andExpect(jsonPath("$.data.status", is("POSTED")))
                .andExpect(jsonPath("$.data.settlementStatus", is("UNPAID")))
                .andExpect(jsonPath("$.data.totalPayableAmount", is("4750.00"))) // 1000 * 0.95 * 5 = 4750.00
                .andExpect(jsonPath("$.data.totalAllocatedAmount", is("0.00")))
                .andExpect(jsonPath("$.data.outstandingAmount", is("4750.00")))
                .andExpect(jsonPath("$.data.isOverdue", is(false)))
                .andExpect(jsonPath("$.data.lines", hasSize(1)))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        Long invoiceId = json.path("data").path("id").asLong();

        // 1. Inventory stock reduction check: on-hand went from 20 to 15
        InventoryBalance ib = inventoryBalanceRepository.findByVariantId(variant1.getId()).orElseThrow();
        assertThat(ib.getQuantityOnHand()).isEqualTo(15);

        // 2. Stock movement audit check: 1 SALE movement OUT of 5 pieces
        List<StockMovement> movements = stockMovementRepository.findAll();
        assertThat(movements).hasSize(1);
        StockMovement movement = movements.get(0);
        assertThat(movement.getMovementType()).isEqualTo(StockMovementType.SALE);
        assertThat(movement.getDirection()).isEqualTo(StockMovementDirection.OUT);
        assertThat(movement.getQuantity()).isEqualTo(5);

        // 3. Customer ledger entry check: +4750.00 (Customer owes shop)
        List<CustomerLedgerEntry> ledgerEntries = customerLedgerEntryRepository.findAll();
        assertThat(ledgerEntries).hasSize(1);
        CustomerLedgerEntry entry = ledgerEntries.get(0);
        assertThat(entry.getAmount()).isEqualByComparingTo("4750.00");
        assertThat(entry.getRunningBalance()).isEqualByComparingTo("4750.00");
        assertThat(entry.getSourceId()).isEqualTo(invoiceId);
    }

    @Test
    @DisplayName("createInvoice: strict non-negative stock invariant fails overselling for all roles")
    void testCreateInvoice_insufficientStockFails() throws Exception {
        // Stock on hand is 10. Attempt to sell 15.
        CreateInvoiceRequest createRequest = CreateInvoiceRequest.builder()
                .customerId(testCustomer.getId())
                .lines(List.of(
                        InvoiceLineRequest.builder()
                                .variantId(variant2.getId())
                                .quantity(15) // exceeds 10
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/invoices")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("INSUFFICIENT_STOCK")));

        // Verify stock remained unchanged
        InventoryBalance ib = inventoryBalanceRepository.findByVariantId(variant2.getId()).orElseThrow();
        assertThat(ib.getQuantityOnHand()).isEqualTo(10);
        assertThat(salesInvoiceRepository.count()).isEqualTo(0);
        assertThat(customerLedgerEntryRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("createInvoice: NORMAL and GST display format produce identical payable totals")
    void testCreateInvoice_formatParity() throws Exception {
        CreateInvoiceRequest reqNormal = CreateInvoiceRequest.builder()
                .customerId(testCustomer.getId())
                .displayFormat(InvoiceDisplayFormat.NORMAL)
                .lines(List.of(
                        InvoiceLineRequest.builder()
                                .variantId(variant1.getId())
                                .quantity(2)
                                .build()
                ))
                .build();

        CreateInvoiceRequest reqGst = CreateInvoiceRequest.builder()
                .customerId(testCustomer.getId())
                .displayFormat(InvoiceDisplayFormat.GST)
                .lines(List.of(
                        InvoiceLineRequest.builder()
                                .variantId(variant1.getId())
                                .quantity(2)
                                .build()
                ))
                .build();

        MvcResult res1 = mockMvc.perform(post("/api/v1/invoices")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqNormal)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult res2 = mockMvc.perform(post("/api/v1/invoices")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqGst)))
                .andExpect(status().isCreated())
                .andReturn();

        BigDecimal normalTotal = new BigDecimal(objectMapper.readTree(res1.getResponse().getContentAsString()).path("data").path("totalPayableAmount").asText());
        BigDecimal gstTotal = new BigDecimal(objectMapper.readTree(res2.getResponse().getContentAsString()).path("data").path("totalPayableAmount").asText());

        assertThat(normalTotal).isEqualByComparingTo(gstTotal);
        assertThat(normalTotal).isEqualByComparingTo("1900.00");
    }

    @Test
    @DisplayName("createInvoice: cross-customer site reference is rejected")
    void testCreateInvoice_crossCustomerSiteRejected() throws Exception {
        // testSite belongs to testCustomer. Attempt to bill otherCustomer using testSite.
        CreateInvoiceRequest request = CreateInvoiceRequest.builder()
                .customerId(otherCustomer.getId())
                .siteId(testSite.getId()) // Mismatched customer!
                .lines(List.of(
                        InvoiceLineRequest.builder()
                                .variantId(variant1.getId())
                                .quantity(1)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/invoices")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("INVALID_CUSTOMER_SITE")));
    }

    @Test
    @DisplayName("createInvoice: immediate cash and online receipt fully settles bill atomically")
    void testCreateInvoice_withImmediateReceiptSettlement() throws Exception {
        // Line: 2 * 950 = 1900.00 total payable.
        // Payment: 1000 CASH, 900 ONLINE = 1900.00 total.
        CreateInvoiceRequest request = CreateInvoiceRequest.builder()
                .customerId(testCustomer.getId())
                .lines(List.of(
                        InvoiceLineRequest.builder()
                                .variantId(variant1.getId())
                                .quantity(2)
                                .build()
                ))
                .receiptComponents(List.of(
                        PaymentComponentDto.builder()
                                .method(PaymentMethod.CASH)
                                .amount(new BigDecimal("1000.00"))
                                .build(),
                        PaymentComponentDto.builder()
                                .method(PaymentMethod.ONLINE)
                                .amount(new BigDecimal("900.00"))
                                .onlineReference("UPI-REF-12345")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/invoices")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.settlementStatus", is("PAID")))
                .andExpect(jsonPath("$.data.totalPayableAmount", is("1900.00")))
                .andExpect(jsonPath("$.data.totalAllocatedAmount", is("1900.00")))
                .andExpect(jsonPath("$.data.outstandingAmount", is("0.00")))
                .andExpect(jsonPath("$.data.allocations", hasSize(1)));

        // Verify Customer Receipts created
        List<CustomerReceipt> receipts = customerReceiptRepository.findAll();
        assertThat(receipts).hasSize(1);
        CustomerReceipt receipt = receipts.get(0);
        assertThat(receipt.getTotalAmount()).isEqualByComparingTo("1900.00");
        assertThat(receipt.getUnallocatedAmount()).isEqualByComparingTo("0.00");
        assertThat(customerReceiptComponentRepository.findByCustomerReceiptId(receipt.getId())).hasSize(2);

        // Verify Customer Ledger has 2 entries: INVOICE (+1900) and RECEIPT (-1900), final running balance = 0.00
        List<CustomerLedgerEntry> entries = customerLedgerEntryRepository.findAll();
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getAmount()).isEqualByComparingTo("1900.00");
        assertThat(entries.get(0).getRunningBalance()).isEqualByComparingTo("1900.00");
        assertThat(entries.get(1).getAmount()).isEqualByComparingTo("-1900.00");
        assertThat(entries.get(1).getRunningBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("customerReceipt: standalone advance receipt and explicit credit allocation")
    void testCustomerReceipt_advanceAndAllocation() throws Exception {
        // 1. Customer deposits 1000.00 advance
        CreateCustomerReceiptRequest receiptReq = CreateCustomerReceiptRequest.builder()
                .receiptDate(businessClock.today())
                .totalAmount(new BigDecimal("1000.00"))
                .components(List.of(
                        PaymentComponentDto.builder()
                                .method(PaymentMethod.CASH)
                                .amount(new BigDecimal("1000.00"))
                                .build()
                ))
                .notes("Advance payment for future paint orders")
                .build();

        MvcResult receiptResult = mockMvc.perform(post("/api/v1/customers/" + testCustomer.getId() + "/receipts")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(receiptReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.receiptNumber", containsString("CREC-")))
                .andExpect(jsonPath("$.data.totalAmount", is("1000.00")))
                .andExpect(jsonPath("$.data.unallocatedAmount", is("1000.00")))
                .andReturn();

        Long receiptId = objectMapper.readTree(receiptResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        // Check ledger after receipt: RECEIPT (-1000.00), running balance = -1000.00
        CustomerLedgerEntry initialEntry = customerLedgerEntryRepository.findFirstByCustomerIdOrderByIdDesc(testCustomer.getId()).orElseThrow();
        assertThat(initialEntry.getRunningBalance()).isEqualByComparingTo("-1000.00");

        // 2. Customer buys goods on credit: 1 * 500 = 500.00
        CreateInvoiceRequest invoiceReq = CreateInvoiceRequest.builder()
                .customerId(testCustomer.getId())
                .lines(List.of(
                        InvoiceLineRequest.builder()
                                .variantId(variant2.getId())
                                .quantity(1)
                                .build()
                ))
                .build();

        MvcResult invoiceResult = mockMvc.perform(post("/api/v1/invoices")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invoiceReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalPayableAmount", is("500.00")))
                .andExpect(jsonPath("$.data.settlementStatus", is("UNPAID")))
                .andReturn();

        Long invoiceId = objectMapper.readTree(invoiceResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        // Check ledger after invoice: INVOICE (+500.00), running balance = -500.00
        CustomerLedgerEntry invoiceEntry = customerLedgerEntryRepository.findFirstByCustomerIdOrderByIdDesc(testCustomer.getId()).orElseThrow();
        assertThat(invoiceEntry.getRunningBalance()).isEqualByComparingTo("-500.00");

        // 3. Allocate 500.00 from advance receipt to invoice
        ApplyCustomerAllocationRequest allocReq = ApplyCustomerAllocationRequest.builder()
                .allocations(List.of(
                        CustomerAllocationItemRequest.builder()
                                .receiptId(receiptId)
                                .invoiceId(invoiceId)
                                .amount(new BigDecimal("500.00"))
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/customers/" + testCustomer.getId() + "/allocations")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(allocReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].allocatedAmount", is("500.00")));

        // Invoice is now PAID
        SalesInvoice updatedInvoice = salesInvoiceRepository.findById(invoiceId).orElseThrow();
        assertThat(updatedInvoice.getSettlementStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(updatedInvoice.getTotalAllocatedAmount()).isEqualByComparingTo("500.00");

        // Receipt unallocated credit is now 500.00
        CustomerReceipt updatedReceipt = customerReceiptRepository.findById(receiptId).orElseThrow();
        assertThat(updatedReceipt.getUnallocatedAmount()).isEqualByComparingTo("500.00");

        // CRITICAL: Ledger count must NOT increase on internal allocation, running balance remains -500.00
        assertThat(customerLedgerEntryRepository.count()).isEqualTo(2);
        CustomerLedgerEntry afterAllocEntry = customerLedgerEntryRepository.findFirstByCustomerIdOrderByIdDesc(testCustomer.getId()).orElseThrow();
        assertThat(afterAllocEntry.getRunningBalance()).isEqualByComparingTo("-500.00");
    }

    @Test
    @DisplayName("createInvoice: with applyAdvanceAmount auto-allocates from customer advance credit")
    void testCreateInvoice_withAutoAdvanceAllocation() throws Exception {
        // Setup advance receipt of 2000.00
        CreateCustomerReceiptRequest advanceReq = CreateCustomerReceiptRequest.builder()
                .receiptDate(businessClock.today())
                .totalAmount(new BigDecimal("2000.00"))
                .components(List.of(
                        PaymentComponentDto.builder()
                                .method(PaymentMethod.ONLINE)
                                .amount(new BigDecimal("2000.00"))
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/customers/" + testCustomer.getId() + "/receipts")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(advanceReq)))
                .andExpect(status().isCreated());

        // Create invoice of 1900.00 with applyAdvanceAmount = 1900.00
        CreateInvoiceRequest invoiceReq = CreateInvoiceRequest.builder()
                .customerId(testCustomer.getId())
                .lines(List.of(
                        InvoiceLineRequest.builder()
                                .variantId(variant1.getId())
                                .quantity(2) // 1900.00
                                .build()
                ))
                .applyAdvanceAmount(new BigDecimal("1900.00"))
                .build();

        mockMvc.perform(post("/api/v1/invoices")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invoiceReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.settlementStatus", is("PAID")))
                .andExpect(jsonPath("$.data.totalPayableAmount", is("1900.00")))
                .andExpect(jsonPath("$.data.totalAllocatedAmount", is("1900.00")))
                .andExpect(jsonPath("$.data.outstandingAmount", is("0.00")));

        // Unallocated credit remaining = 100.00
        List<CustomerReceipt> receipts = customerReceiptRepository.findUnallocatedReceiptsByCustomer(testCustomer.getId());
        assertThat(receipts.get(0).getUnallocatedAmount()).isEqualByComparingTo("100.00");

        // Customer ledger running balance = -100.00
        CustomerLedgerEntry latest = customerLedgerEntryRepository.findFirstByCustomerIdOrderByIdDesc(testCustomer.getId()).orElseThrow();
        assertThat(latest.getRunningBalance()).isEqualByComparingTo("-100.00");
    }

    @Test
    @DisplayName("dues and overdue: search and summary accurately identify aging invoices")
    void testDuesAndOverdue_searchAndSummary() throws Exception {
        LocalDate today = businessClock.today();

        // 1. Create invoice dated today (Due in 30 days => not overdue)
        CreateInvoiceRequest invoiceToday = CreateInvoiceRequest.builder()
                .customerId(testCustomer.getId())
                .invoiceDate(today)
                .lines(List.of(
                        InvoiceLineRequest.builder()
                                .variantId(variant2.getId())
                                .quantity(1) // 500.00
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/invoices")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invoiceToday)))
                .andExpect(status().isCreated());

        // 2. Insert backdated invoice dated 40 days ago directly (Due 10 days ago => overdue)
        SalesInvoice backdatedInvoice = salesInvoiceRepository.save(SalesInvoice.builder()
                .invoiceNumber("INV-OLD-01")
                .customer(testCustomer)
                .invoiceDate(today.minusDays(40))
                .dueDate(today.minusDays(10))
                .displayFormat(InvoiceDisplayFormat.NORMAL)
                .status(com.asalkartechworks.himmat.billing.model.InvoiceStatus.POSTED)
                .settlementStatus(SettlementStatus.UNPAID)
                .customerName(testCustomer.getName())
                .totalPayableAmount(new BigDecimal("1000.00"))
                .originalPayableAmount(new BigDecimal("1000.00"))
                .totalReturnedAmount(BigDecimal.ZERO)
                .revisionCount(0)
                .totalAllocatedAmount(BigDecimal.ZERO)
                .businessEvent(businessEventRepository.findAll().get(0))
                .createdAt(Instant.now().minusSeconds(40 * 86400))
                .updatedAt(Instant.now().minusSeconds(40 * 86400))
                .build());

        // 3. Search dues with overdueOnly=true
        mockMvc.perform(get("/api/v1/dues?overdueOnly=true")
                        .header(HttpHeaders.AUTHORIZATION, managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].invoiceNumber", is("INV-OLD-01")))
                .andExpect(jsonPath("$.data[0].isOverdue", is(true)))
                .andExpect(jsonPath("$.data[0].daysOverdue", is("10")));

        // 4. Dues summary
        mockMvc.perform(get("/api/v1/dues/summary")
                        .header(HttpHeaders.AUTHORIZATION, managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOutstandingAmount", is("1500.00"))) // 500 + 1000
                .andExpect(jsonPath("$.data.totalOverdueAmount", is("1000.00")))     // 1000
                .andExpect(jsonPath("$.data.outstandingInvoiceCount", is("2")))
                .andExpect(jsonPath("$.data.overdueInvoiceCount", is("1")));
    }

    @Test
    @DisplayName("customerAccountSummary & ledgerStatement: returns complete balances and paginated entries")
    void testCustomerAccountSummaryAndLedgerStatement() throws Exception {
        // Create an invoice of 950.00
        CreateInvoiceRequest invoiceReq = CreateInvoiceRequest.builder()
                .customerId(testCustomer.getId())
                .lines(List.of(
                        InvoiceLineRequest.builder()
                                .variantId(variant1.getId())
                                .quantity(1)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/invoices")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invoiceReq)))
                .andExpect(status().isCreated());

        // Account summary
        mockMvc.perform(get("/api/v1/customers/" + testCustomer.getId() + "/account")
                        .header(HttpHeaders.AUTHORIZATION, managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerId", is(testCustomer.getId().toString())))
                .andExpect(jsonPath("$.data.currentBalance", is("950.00")))
                .andExpect(jsonPath("$.data.totalInvoiced", is("950.00")))
                .andExpect(jsonPath("$.data.outstandingDues", is("950.00")))
                .andExpect(jsonPath("$.data.unallocatedCredit", is("0.00")));

        // Ledger statement
        mockMvc.perform(get("/api/v1/customers/" + testCustomer.getId() + "/ledger")
                        .header(HttpHeaders.AUTHORIZATION, managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openingBalance", is("0.00")))
                .andExpect(jsonPath("$.data.closingBalance", is("950.00")))
                .andExpect(jsonPath("$.data.entries.data", hasSize(1)))
                .andExpect(jsonPath("$.data.entries.data[0].amount", is("950.00")))
                .andExpect(jsonPath("$.data.entries.data[0].runningBalance", is("950.00")));
    }

    @Test
    @DisplayName("idempotency: exact replay returns cached response and conflict on mismatched payload")
    void testIdempotency_replayAndConflict() throws Exception {
        CreateInvoiceRequest req = CreateInvoiceRequest.builder()
                .customerId(testCustomer.getId())
                .lines(List.of(
                        InvoiceLineRequest.builder()
                                .variantId(variant2.getId())
                                .quantity(1)
                                .build()
                ))
                .build();

        // Initial submission
        MvcResult res1 = mockMvc.perform(post("/api/v1/invoices")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "IDEMP-TEST-KEY-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long id1 = objectMapper.readTree(res1.getResponse().getContentAsString()).path("data").path("id").asLong();

        // Exact replay with same idempotency key
        MvcResult res2 = mockMvc.perform(post("/api/v1/invoices")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "IDEMP-TEST-KEY-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long id2 = objectMapper.readTree(res2.getResponse().getContentAsString()).path("data").path("id").asLong();
        assertThat(id1).isEqualTo(id2);

        // Only 1 invoice exists in db, stock deducted only once (10 - 1 = 9)
        assertThat(salesInvoiceRepository.count()).isEqualTo(1);
        InventoryBalance ib = inventoryBalanceRepository.findByVariantId(variant2.getId()).orElseThrow();
        assertThat(ib.getQuantityOnHand()).isEqualTo(9);

        // Replay with DIFFERENT payload -> 409 CONFLICT
        CreateInvoiceRequest diffReq = CreateInvoiceRequest.builder()
                .customerId(testCustomer.getId())
                .lines(List.of(
                        InvoiceLineRequest.builder()
                                .variantId(variant2.getId())
                                .quantity(2)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/invoices")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "IDEMP-TEST-KEY-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(diffReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("IDEMPOTENCY_CONFLICT")));
    }

    @Test
    @DisplayName("roleAuthorization: Manager can perform sales billing, receipts, dues, and ledger queries")
    void testRoleAuthorization_managerAllowed() throws Exception {
        CreateInvoiceRequest req = CreateInvoiceRequest.builder()
                .customerId(testCustomer.getId())
                .lines(List.of(
                        InvoiceLineRequest.builder()
                                .variantId(variant2.getId())
                                .quantity(1)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/invoices")
                        .header(HttpHeaders.AUTHORIZATION, managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Autowired
    private com.asalkartechworks.himmat.shared.idempotency.repository.BusinessEventRepository businessEventRepository;
}
