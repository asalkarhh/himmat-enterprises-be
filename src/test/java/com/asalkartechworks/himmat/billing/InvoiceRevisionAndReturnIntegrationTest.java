package com.asalkartechworks.himmat.billing;

import com.asalkartechworks.himmat.billing.dto.CreateCustomerReceiptRequest;
import com.asalkartechworks.himmat.billing.dto.CreateCustomerRefundRequest;
import com.asalkartechworks.himmat.billing.dto.CreateInvoiceRequest;
import com.asalkartechworks.himmat.billing.dto.CustomerReturnLineRequest;
import com.asalkartechworks.himmat.billing.dto.CustomerReturnRequest;
import com.asalkartechworks.himmat.billing.dto.InvoiceAllocationRequest;
import com.asalkartechworks.himmat.billing.dto.InvoiceLineRequest;
import com.asalkartechworks.himmat.billing.dto.InvoiceRevisionLineRequest;
import com.asalkartechworks.himmat.billing.dto.InvoiceRevisionRequest;
import com.asalkartechworks.himmat.billing.model.CustomerLedgerEntry;
import com.asalkartechworks.himmat.billing.model.CustomerReceipt;
import com.asalkartechworks.himmat.billing.model.CustomerReturn;
import com.asalkartechworks.himmat.billing.model.SalesInvoice;
import com.asalkartechworks.himmat.billing.model.SalesInvoiceRevision;
import com.asalkartechworks.himmat.billing.model.SettlementStatus;
import com.asalkartechworks.himmat.billing.repository.CustomerLedgerEntryRepository;
import com.asalkartechworks.himmat.billing.repository.CustomerReceiptRepository;
import com.asalkartechworks.himmat.billing.repository.CustomerRefundRepository;
import com.asalkartechworks.himmat.billing.repository.CustomerReturnRepository;
import com.asalkartechworks.himmat.billing.repository.ReceiptAllocationRepository;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceLineRepository;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceRepository;
import com.asalkartechworks.himmat.billing.repository.SalesInvoiceRevisionRepository;
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
import com.asalkartechworks.himmat.party.model.CustomerType;
import com.asalkartechworks.himmat.party.repository.CustomerRepository;
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
class InvoiceRevisionAndReturnIntegrationTest {

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
    private SalesInvoiceRevisionRepository salesInvoiceRevisionRepository;

    @Autowired
    private CustomerReturnRepository customerReturnRepository;

    @Autowired
    private CustomerRefundRepository customerRefundRepository;

    @Autowired
    private CustomerReceiptRepository customerReceiptRepository;

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
    private String ownerToken;

    private Customer testCustomer;
    private Brand testBrand;
    private Category testCategory;
    private Subcategory testSubcategory;
    private Product testProduct;
    private ProductVariant variant1; // Fixed price 100.00
    private ProductVariant variant2; // Fixed price 200.00

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE customer_refund_source, customer_refund_component, customer_refund, customer_return_line, customer_return, sales_invoice_revision_line, sales_invoice_revision, customer_ledger_entry, receipt_allocation, customer_receipt_component, customer_receipt, sales_invoice_line, sales_invoice, customer_site, customer, supplier_ledger_entry, payment_allocation, payment_component, supplier_refund, purchase_return_line, purchase_return, supplier_payment, purchase_line, purchase, stock_movement, inventory_balance, product_variant, product, subcategory, category, brand, supplier, business_event, app_user CASCADE");

        Instant now = Instant.now();

        // 1. Setup User
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

        ownerToken = "Bearer " + jwtService.generateToken(UserPrincipal.create(ownerUser));

        // 2. Setup Customer
        testCustomer = customerRepository.save(Customer.builder()
                .customerCode("CUST-REV01")
                .name("Ramesh Builders")
                .normalizedName("ramesh builders")
                .phone("9876543210")
                .customerType(CustomerType.CONTRACTOR)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // 3. Catalog setup
        testBrand = brandRepository.save(Brand.builder()
                .name("Asian Paints")
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        testCategory = categoryRepository.save(Category.builder()
                .name("Emulsion Paints")
                .salesDiscountPct(BigDecimal.ZERO)
                .purchaseDiscountPct(BigDecimal.ZERO)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        testSubcategory = subcategoryRepository.save(Subcategory.builder()
                .category(testCategory)
                .name("Interior Emulsion")
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        testProduct = productRepository.save(Product.builder()
                .brand(testBrand)
                .subcategory(testSubcategory)
                .name("Apcolite Premium Emulsion")
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        variant1 = productVariantRepository.save(ProductVariant.builder()
                .product(testProduct)
                .sku("APE-1L-WHITE")
                .sizeLabel("1L")
                .pricingMethod(PricingMethod.FIXED)
                .fixedPriceInclTax(new BigDecimal("100.00"))
                .purchaseCostExclTax(new BigDecimal("70.00"))
                .gstRatePct(new BigDecimal("18.0000"))
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        inventoryBalanceRepository.save(InventoryBalance.builder()
                .variantId(variant1.getId())
                .variant(variant1)
                .quantityOnHand(100)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        variant2 = productVariantRepository.save(ProductVariant.builder()
                .product(testProduct)
                .sku("APE-4L-WHITE")
                .sizeLabel("4L")
                .pricingMethod(PricingMethod.FIXED)
                .fixedPriceInclTax(new BigDecimal("200.00"))
                .purchaseCostExclTax(new BigDecimal("140.00"))
                .gstRatePct(new BigDecimal("18.0000"))
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        inventoryBalanceRepository.save(InventoryBalance.builder()
                .variantId(variant2.getId())
                .variant(variant2)
                .quantityOnHand(50)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private Long createInvoice(int qty1, int qty2) throws Exception {
        java.util.List<InvoiceLineRequest> lines = new java.util.ArrayList<>();
        if (qty1 > 0) {
            lines.add(InvoiceLineRequest.builder().variantId(variant1.getId()).quantity(qty1).build());
        }
        if (qty2 > 0) {
            lines.add(InvoiceLineRequest.builder().variantId(variant2.getId()).quantity(qty2).build());
        }
        CreateInvoiceRequest request = CreateInvoiceRequest.builder()
                .customerId(testCustomer.getId())
                .invoiceDate(businessClock.today())
                .lines(lines)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/invoices")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "INV-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("id").asLong();
    }

    @Test
    @DisplayName("Revision: Quantity increase deducts additional stock (OUT, SALE)")
    void testRevision_QuantityIncrease_DeductsAdditionalStock() throws Exception {
        Long invoiceId = createInvoice(10, 0); // 10 * 100 = 1000. Stock was 100 -> now 90
        SalesInvoice invoice = salesInvoiceRepository.findById(invoiceId).orElseThrow();
        Long lineId = salesInvoiceLineRepository.findByInvoiceId(invoiceId).get(0).getId();

        assertThat(inventoryBalanceRepository.findByVariantId(variant1.getId()).orElseThrow().getQuantityOnHand()).isEqualTo(90);

        // Preview revision: increase to 15
        InvoiceRevisionRequest revReq = InvoiceRevisionRequest.builder()
                .expectedVersion(invoice.getVersion())
                .reason("Customer added 5 more cans")
                .lines(List.of(
                        InvoiceRevisionLineRequest.builder()
                                .invoiceLineId(lineId)
                                .variantId(variant1.getId())
                                .quantity(15)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/revisions/preview")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newTotalPayable", is("1500.00")))
                .andExpect(jsonPath("$.data.deltaPayableAmount", is("500.00")))
                .andExpect(jsonPath("$.data.lines[0].changeType", is("MODIFIED")))
                .andExpect(jsonPath("$.data.lines[0].deltaQuantity", is(5)));

        // Apply revision
        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/revisions")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "REV-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.revisionNumber", is(1)))
                .andExpect(jsonPath("$.data.newTotalPayable", is("1500.00")))
                .andExpect(jsonPath("$.data.deltaPayableAmount", is("500.00")));

        // Verify stock deducted by 5 (90 -> 85)
        assertThat(inventoryBalanceRepository.findByVariantId(variant1.getId()).orElseThrow().getQuantityOnHand()).isEqualTo(85);

        // Verify invoice updated
        SalesInvoice updated = salesInvoiceRepository.findById(invoiceId).orElseThrow();
        assertThat(updated.getTotalPayableAmount()).isEqualByComparingTo("1500.00");
        assertThat(updated.getOriginalPayableAmount()).isEqualByComparingTo("1000.00");
        assertThat(updated.getRevisionCount()).isEqualTo(1);

        // Verify ledger entry added for +500
        List<CustomerLedgerEntry> entries = customerLedgerEntryRepository.findAll();
        assertThat(entries).hasSize(2); // Invoice (1000) + Revision (500)
        CustomerLedgerEntry lastEntry = customerLedgerEntryRepository.findFirstByCustomerIdOrderByIdDesc(testCustomer.getId()).orElseThrow();
        assertThat(lastEntry.getAmount()).isEqualByComparingTo("500.00");
        assertThat(lastEntry.getRunningBalance()).isEqualByComparingTo("1500.00");
    }

    @Test
    @DisplayName("Revision: Quantity decrease restores difference to stock (IN, REVERSAL)")
    void testRevision_QuantityDecrease_RestoresStockReversal() throws Exception {
        Long invoiceId = createInvoice(10, 0); // Stock 100 -> 90
        SalesInvoice invoice = salesInvoiceRepository.findById(invoiceId).orElseThrow();
        Long lineId = salesInvoiceLineRepository.findByInvoiceId(invoiceId).get(0).getId();

        InvoiceRevisionRequest revReq = InvoiceRevisionRequest.builder()
                .expectedVersion(invoice.getVersion())
                .reason("Customer took 6 cans instead of 10")
                .lines(List.of(
                        InvoiceRevisionLineRequest.builder()
                                .invoiceLineId(lineId)
                                .variantId(variant1.getId())
                                .quantity(6)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/revisions")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "REV-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.newTotalPayable", is("600.00")))
                .andExpect(jsonPath("$.data.deltaPayableAmount", is("-400.00")));

        // Stock restored from 90 -> 94
        assertThat(inventoryBalanceRepository.findByVariantId(variant1.getId()).orElseThrow().getQuantityOnHand()).isEqualTo(94);

        // Verify StockMovement recorded as REVERSAL
        List<StockMovement> movements = stockMovementRepository.findAll();
        StockMovement reversal = movements.get(movements.size() - 1);
        assertThat(reversal.getMovementType()).isEqualTo(StockMovementType.REVERSAL);
        assertThat(reversal.getDirection()).isEqualTo(StockMovementDirection.IN);
        assertThat(reversal.getQuantity()).isEqualTo(4);

        // Verify ledger entry: -400, running balance 600
        CustomerLedgerEntry lastEntry = customerLedgerEntryRepository.findFirstByCustomerIdOrderByIdDesc(testCustomer.getId()).orElseThrow();
        assertThat(lastEntry.getAmount()).isEqualByComparingTo("-400.00");
        assertThat(lastEntry.getRunningBalance()).isEqualByComparingTo("600.00");
    }

    @Test
    @DisplayName("Revision: Price-only correction produces 0 stock movements")
    void testRevision_PriceOnlyCorrection_ZeroStockMovement() throws Exception {
        Long invoiceId = createInvoice(10, 0); // 10 * 100 = 1000. Stock = 90
        SalesInvoice invoice = salesInvoiceRepository.findById(invoiceId).orElseThrow();
        Long lineId = salesInvoiceLineRepository.findByInvoiceId(invoiceId).get(0).getId();

        long stockMovementsBefore = stockMovementRepository.count();

        // Price override from 100 to 90
        InvoiceRevisionRequest revReq = InvoiceRevisionRequest.builder()
                .expectedVersion(invoice.getVersion())
                .reason("Negotiated retroactive discount to 90/can")
                .lines(List.of(
                        InvoiceRevisionLineRequest.builder()
                                .invoiceLineId(lineId)
                                .variantId(variant1.getId())
                                .quantity(10)
                                .unitPriceOverride(new BigDecimal("90.00"))
                                .overrideReason("Loyal customer volume discount")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/revisions")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "REV-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.newTotalPayable", is("900.00")))
                .andExpect(jsonPath("$.data.deltaPayableAmount", is("-100.00")))
                .andExpect(jsonPath("$.data.lines[0].deltaQuantity", is(0)));

        // Assert 0 new stock movements
        assertThat(stockMovementRepository.count()).isEqualTo(stockMovementsBefore);
        assertThat(inventoryBalanceRepository.findByVariantId(variant1.getId()).orElseThrow().getQuantityOnHand()).isEqualTo(90);

        // Ledger adjusted by -100
        CustomerLedgerEntry lastEntry = customerLedgerEntryRepository.findFirstByCustomerIdOrderByIdDesc(testCustomer.getId()).orElseThrow();
        assertThat(lastEntry.getAmount()).isEqualByComparingTo("-100.00");
        assertThat(lastEntry.getRunningBalance()).isEqualByComparingTo("900.00");
    }

    @Test
    @DisplayName("Revision: Preserves original invoice number, invoice date, and due date")
    void testRevision_PreservesOriginalInvoiceIdentityAndDates() throws Exception {
        Long invoiceId = createInvoice(5, 5);
        SalesInvoice original = salesInvoiceRepository.findById(invoiceId).orElseThrow();
        String originalInvoiceNumber = original.getInvoiceNumber();
        LocalDate originalInvoiceDate = original.getInvoiceDate();
        LocalDate originalDueDate = original.getDueDate();
        List<com.asalkartechworks.himmat.billing.model.SalesInvoiceLine> lines = salesInvoiceLineRepository.findByInvoiceId(invoiceId);
        Long lineId1 = lines.get(0).getId();
        Long lineId2 = lines.get(1).getId();

        InvoiceRevisionRequest revReq = InvoiceRevisionRequest.builder()
                .expectedVersion(original.getVersion())
                .reason("Adjustment")
                .lines(List.of(
                        InvoiceRevisionLineRequest.builder().invoiceLineId(lineId1).variantId(variant1.getId()).quantity(6).build(),
                        InvoiceRevisionLineRequest.builder().invoiceLineId(lineId2).variantId(variant2.getId()).quantity(4).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/revisions")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "REV-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.invoiceNumber", is(originalInvoiceNumber)));

        SalesInvoice revised = salesInvoiceRepository.findById(invoiceId).orElseThrow();
        assertThat(revised.getInvoiceNumber()).isEqualTo(originalInvoiceNumber);
        assertThat(revised.getInvoiceDate()).isEqualTo(originalInvoiceDate);
        assertThat(revised.getDueDate()).isEqualTo(originalDueDate);
        assertThat(revised.getRevisionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Revision: Rejects revision on stale expectedVersion (409 CONFLICT)")
    void testRevision_StaleVersionConflict() throws Exception {
        Long invoiceId = createInvoice(5, 0);
        SalesInvoice invoice = salesInvoiceRepository.findById(invoiceId).orElseThrow();
        Long lineId = salesInvoiceLineRepository.findByInvoiceId(invoiceId).get(0).getId();

        InvoiceRevisionRequest revReq = InvoiceRevisionRequest.builder()
                .expectedVersion(999L) // Stale version
                .reason("Stale edit")
                .lines(List.of(
                        InvoiceRevisionLineRequest.builder().invoiceLineId(lineId).variantId(variant1.getId()).quantity(8).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/revisions")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "REV-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("STALE_VERSION")));
    }

    @Test
    @DisplayName("Customer Return: Accepted beyond 30 days without arbitrary time restriction")
    void testCustomerReturn_AcceptedBeyond30Days() throws Exception {
        Long invoiceId = createInvoice(10, 0); // 10 * 100 = 1000
        SalesInvoice invoice = salesInvoiceRepository.findById(invoiceId).orElseThrow();
        Long lineId = salesInvoiceLineRepository.findByInvoiceId(invoiceId).get(0).getId();

        // Customer brings 3 cans back after 45 days
        LocalDate returnDate = businessClock.today().plusDays(45);

        CustomerReturnRequest retReq = CustomerReturnRequest.builder()
                .returnDate(returnDate)
                .reason("Customer project finished with surplus cans")
                .lines(List.of(
                        CustomerReturnLineRequest.builder()
                                .invoiceLineId(lineId)
                                .quantity(3)
                                .build()
                ))
                .build();

        // Preview return
        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/returns/preview")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(retReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalReturnAmount", is("300.00")))
                .andExpect(jsonPath("$.data.newEffectivePayableAmount", is("700.00")));

        // Create return
        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/returns")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "RET-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(retReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalReturnAmount", is("300.00")))
                .andExpect(jsonPath("$.data.lines[0].returnedQuantity", is(3)));

        // Verify stock returned (90 + 3 = 93)
        assertThat(inventoryBalanceRepository.findByVariantId(variant1.getId()).orElseThrow().getQuantityOnHand()).isEqualTo(93);

        // Verify invoice effective balance
        SalesInvoice updated = salesInvoiceRepository.findById(invoiceId).orElseThrow();
        assertThat(updated.getTotalReturnedAmount()).isEqualByComparingTo("300.00");
        assertThat(updated.getEffectivePayableAmount()).isEqualByComparingTo("700.00");
        assertThat(updated.getOutstandingAmount()).isEqualByComparingTo("700.00");

        // Verify ledger entry: -300.00
        CustomerLedgerEntry lastEntry = customerLedgerEntryRepository.findFirstByCustomerIdOrderByIdDesc(testCustomer.getId()).orElseThrow();
        assertThat(lastEntry.getAmount()).isEqualByComparingTo("-300.00");
        assertThat(lastEntry.getRunningBalance()).isEqualByComparingTo("700.00");
    }

    @Test
    @DisplayName("Customer Return: Cannot exceed remaining returnable quantity (422 EXCESS_RETURN_QUANTITY)")
    void testCustomerReturn_CannotExceedReturnableQuantity() throws Exception {
        Long invoiceId = createInvoice(5, 0); // 5 items
        SalesInvoice invoice = salesInvoiceRepository.findById(invoiceId).orElseThrow();
        Long lineId = salesInvoiceLineRepository.findByInvoiceId(invoiceId).get(0).getId();

        CustomerReturnRequest retReq = CustomerReturnRequest.builder()
                .reason("Too many returns")
                .lines(List.of(
                        CustomerReturnLineRequest.builder()
                                .invoiceLineId(lineId)
                                .quantity(6) // 6 > 5
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/returns")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "RET-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(retReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("EXCESS_RETURN_QUANTITY")));
    }

    @Test
    @DisplayName("Customer Return: Ambiguous price-revised line requires explicit valuation policy")
    void testCustomerReturn_AmbiguousPriceRevisedLine() throws Exception {
        Long invoiceId = createInvoice(10, 0); // 10 * 100 = 1000
        SalesInvoice invoice = salesInvoiceRepository.findById(invoiceId).orElseThrow();
        Long lineId = salesInvoiceLineRepository.findByInvoiceId(invoiceId).get(0).getId();

        // 1. Revise price from 100 to 120
        InvoiceRevisionRequest revReq = InvoiceRevisionRequest.builder()
                .expectedVersion(invoice.getVersion())
                .reason("Price corrected upwards to 120")
                .lines(List.of(
                        InvoiceRevisionLineRequest.builder()
                                .invoiceLineId(lineId)
                                .variantId(variant1.getId())
                                .quantity(10)
                                .unitPriceOverride(new BigDecimal("120.00"))
                                .overrideReason("Correction")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/revisions")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "REV-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revReq)))
                .andExpect(status().isCreated());

        // 2. Attempt return without valuation policy -> MUST FAIL 422
        CustomerReturnRequest retReqNoPolicy = CustomerReturnRequest.builder()
                .reason("Return without specifying policy")
                .lines(List.of(
                        CustomerReturnLineRequest.builder()
                                .invoiceLineId(lineId)
                                .quantity(2)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/returns")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "RET-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(retReqNoPolicy)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("RETURN_VALUATION_POLICY_REQUIRED")));

        // 3. Provide explicit valuation policy: ORIGINAL_INVOICE_PRICE (100.00)
        CustomerReturnRequest retReqWithPolicy = CustomerReturnRequest.builder()
                .reason("Return with explicit policy")
                .valuationPolicy("ORIGINAL_INVOICE_PRICE")
                .lines(List.of(
                        CustomerReturnLineRequest.builder()
                                .invoiceLineId(lineId)
                                .quantity(2)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/returns")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "RET-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(retReqWithPolicy)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalReturnAmount", is("200.00")))
                .andExpect(jsonPath("$.data.lines[0].unitPrice", is("100.0000")))
                .andExpect(jsonPath("$.data.lines[0].valuationBasis", is("ORIGINAL_INVOICE_PRICE")));
    }

    @Test
    @DisplayName("Paid Return: Releases excess allocation to customer receipt credit")
    void testPaidReturn_ReleasesExcessAllocationToReceiptCredit() throws Exception {
        Long invoiceId = createInvoice(10, 0); // 1000.00
        SalesInvoice invoice = salesInvoiceRepository.findById(invoiceId).orElseThrow();
        Long lineId = salesInvoiceLineRepository.findByInvoiceId(invoiceId).get(0).getId();

        // Customer pays 1000 in full
        CreateCustomerReceiptRequest receiptReq = CreateCustomerReceiptRequest.builder()
                .receiptDate(businessClock.today())
                .totalAmount(new BigDecimal("1000.00"))
                .components(List.of(
                        PaymentComponentDto.builder().method(PaymentMethod.CASH).amount(new BigDecimal("1000.00")).build()
                ))
                .allocations(List.of(
                        InvoiceAllocationRequest.builder().invoiceId(invoiceId).amount(new BigDecimal("1000.00")).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/customers/" + testCustomer.getId() + "/receipts")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "REC-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(receiptReq)))
                .andExpect(status().isCreated());

        SalesInvoice paidInvoice = salesInvoiceRepository.findById(invoiceId).orElseThrow();
        assertThat(paidInvoice.getSettlementStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(paidInvoice.getTotalAllocatedAmount()).isEqualByComparingTo("1000.00");

        // Customer returns 4 items = 400.00
        CustomerReturnRequest retReq = CustomerReturnRequest.builder()
                .reason("Customer return on fully paid invoice")
                .lines(List.of(
                        CustomerReturnLineRequest.builder()
                                .invoiceLineId(lineId)
                                .quantity(4)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/returns")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "RET-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(retReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalReturnAmount", is("400.00")))
                .andExpect(jsonPath("$.data.releasedAllocationAmount", is("400.00")));

        // Invoice allocated amount updated to 600, still PAID
        SalesInvoice afterReturn = salesInvoiceRepository.findById(invoiceId).orElseThrow();
        assertThat(afterReturn.getTotalAllocatedAmount()).isEqualByComparingTo("600.00");
        assertThat(afterReturn.getEffectivePayableAmount()).isEqualByComparingTo("600.00");
        assertThat(afterReturn.getSettlementStatus()).isEqualTo(SettlementStatus.PAID);

        // Receipt unallocated credit is now 400.00!
        List<CustomerReceipt> receipts = customerReceiptRepository.findAll();
        assertThat(receipts.get(0).getUnallocatedAmount()).isEqualByComparingTo("400.00");

        // Ledger balance is -400.00 (shop holds 400 customer advance)
        CustomerLedgerEntry lastEntry = customerLedgerEntryRepository.findFirstByCustomerIdOrderByIdDesc(testCustomer.getId()).orElseThrow();
        assertThat(lastEntry.getRunningBalance()).isEqualByComparingTo("-400.00");
    }

    @Test
    @DisplayName("Customer Refund: Consumes unallocated receipt credit and reconciles ledger balance to zero")
    void testCustomerRefund_ConsumesReceiptCreditAndReconcilesLedger() throws Exception {
        Long invoiceId = createInvoice(10, 0); // 1000.00
        SalesInvoice invoice = salesInvoiceRepository.findById(invoiceId).orElseThrow();
        Long lineId = salesInvoiceLineRepository.findByInvoiceId(invoiceId).get(0).getId();

        // Customer paid 1000, returns 500
        CreateCustomerReceiptRequest receiptReq = CreateCustomerReceiptRequest.builder()
                .receiptDate(businessClock.today())
                .totalAmount(new BigDecimal("1000.00"))
                .components(List.of(PaymentComponentDto.builder().method(PaymentMethod.CASH).amount(new BigDecimal("1000.00")).build()))
                .allocations(List.of(InvoiceAllocationRequest.builder().invoiceId(invoiceId).amount(new BigDecimal("1000.00")).build()))
                .build();

        mockMvc.perform(post("/api/v1/customers/" + testCustomer.getId() + "/receipts")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "REC-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(receiptReq)))
                .andExpect(status().isCreated());

        CustomerReturnRequest retReq = CustomerReturnRequest.builder()
                .reason("Return 5 items")
                .lines(List.of(CustomerReturnLineRequest.builder().invoiceLineId(lineId).quantity(5).build()))
                .build();

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/returns")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "RET-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(retReq)))
                .andExpect(status().isCreated());

        // Now customer has 500 credit on receipt, and ledger balance is -500.00
        CustomerLedgerEntry balanceBeforeRefund = customerLedgerEntryRepository.findFirstByCustomerIdOrderByIdDesc(testCustomer.getId()).orElseThrow();
        assertThat(balanceBeforeRefund.getRunningBalance()).isEqualByComparingTo("-500.00");

        // Disburse 500 refund (split: 200 CASH, 300 ONLINE)
        CreateCustomerRefundRequest refundReq = CreateCustomerRefundRequest.builder()
                .refundDate(businessClock.today())
                .totalAmount(new BigDecimal("500.00"))
                .reason("Cash & UPI refund for returned paint")
                .components(List.of(
                        PaymentComponentDto.builder().method(PaymentMethod.CASH).amount(new BigDecimal("200.00")).build(),
                        PaymentComponentDto.builder().method(PaymentMethod.ONLINE).amount(new BigDecimal("300.00")).onlineReference("UPI-12345678").build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/customers/" + testCustomer.getId() + "/refunds")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "REF-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refundReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.refundNumber", containsString("CRF-")))
                .andExpect(jsonPath("$.data.totalAmount", is("500.00")))
                .andExpect(jsonPath("$.data.components", hasSize(2)))
                .andExpect(jsonPath("$.data.sources", hasSize(1)))
                .andExpect(jsonPath("$.data.sources[0].amount", is("500.00")));

        // Receipt unallocated amount is now 0.00
        List<CustomerReceipt> receipts = customerReceiptRepository.findAll();
        assertThat(receipts.get(0).getUnallocatedAmount()).isEqualByComparingTo("0.00");

        // Customer ledger running balance is now 0.00!
        CustomerLedgerEntry lastEntry = customerLedgerEntryRepository.findFirstByCustomerIdOrderByIdDesc(testCustomer.getId()).orElseThrow();
        assertThat(lastEntry.getAmount()).isEqualByComparingTo("500.00");
        assertThat(lastEntry.getRunningBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Idempotency: Replay of Revision, Return, and Refund returns exact same result")
    void testIdempotency_ReplaySafe() throws Exception {
        Long invoiceId = createInvoice(10, 0);
        SalesInvoice invoice = salesInvoiceRepository.findById(invoiceId).orElseThrow();
        Long lineId = salesInvoiceLineRepository.findByInvoiceId(invoiceId).get(0).getId();

        String revKey = "REV-IDEMP-" + UUID.randomUUID();
        InvoiceRevisionRequest revReq = InvoiceRevisionRequest.builder()
                .expectedVersion(invoice.getVersion())
                .reason("Idempotent revision")
                .lines(List.of(InvoiceRevisionLineRequest.builder().invoiceLineId(lineId).variantId(variant1.getId()).quantity(8).build()))
                .build();

        // First call
        MvcResult rev1 = mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/revisions")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", revKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revReq)))
                .andExpect(status().isCreated())
                .andReturn();

        // Second call with same key
        MvcResult rev2 = mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/revisions")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", revKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revReq)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode data1 = objectMapper.readTree(rev1.getResponse().getContentAsString()).path("data");
        JsonNode data2 = objectMapper.readTree(rev2.getResponse().getContentAsString()).path("data");
        assertThat(data1).isEqualTo(data2);
        assertThat(salesInvoiceRevisionRepository.findByInvoiceIdOrderByRevisionNumberAsc(invoiceId)).hasSize(1);
    }
}
