package com.asalkartechworks.himmat.purchase;

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
import com.asalkartechworks.himmat.identity.model.AppUser;
import com.asalkartechworks.himmat.identity.model.UserRole;
import com.asalkartechworks.himmat.identity.repository.AppUserRepository;
import com.asalkartechworks.himmat.inventory.model.StockMovement;
import com.asalkartechworks.himmat.inventory.model.StockMovementDirection;
import com.asalkartechworks.himmat.inventory.model.StockMovementType;
import com.asalkartechworks.himmat.inventory.repository.StockMovementRepository;
import com.asalkartechworks.himmat.party.model.Supplier;
import com.asalkartechworks.himmat.party.repository.SupplierRepository;
import com.asalkartechworks.himmat.purchase.dto.AllocationItemRequest;
import com.asalkartechworks.himmat.purchase.dto.ApplySupplierAllocationRequest;
import com.asalkartechworks.himmat.purchase.dto.CreatePurchaseRequest;
import com.asalkartechworks.himmat.purchase.dto.CreatePurchaseReturnRequest;
import com.asalkartechworks.himmat.purchase.dto.CreateSupplierPaymentRequest;
import com.asalkartechworks.himmat.purchase.dto.CreateSupplierRefundRequest;
import com.asalkartechworks.himmat.purchase.dto.PaymentAllocationRequest;
import com.asalkartechworks.himmat.purchase.dto.PaymentComponentDto;
import com.asalkartechworks.himmat.purchase.dto.PurchaseLineRequest;
import com.asalkartechworks.himmat.purchase.dto.PurchaseReturnLineRequest;
import com.asalkartechworks.himmat.purchase.model.PaymentMethod;
import com.asalkartechworks.himmat.purchase.model.PriceBasis;
import com.asalkartechworks.himmat.purchase.model.Purchase;
import com.asalkartechworks.himmat.purchase.model.PurchaseLine;
import com.asalkartechworks.himmat.purchase.model.SupplierLedgerEntry;
import com.asalkartechworks.himmat.purchase.model.SupplierLedgerSourceType;
import com.asalkartechworks.himmat.purchase.repository.PurchaseLineRepository;
import com.asalkartechworks.himmat.purchase.repository.PurchaseRepository;
import com.asalkartechworks.himmat.purchase.repository.SupplierLedgerEntryRepository;
import com.asalkartechworks.himmat.purchase.repository.SupplierPaymentRepository;
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
class PurchaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

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
    private com.asalkartechworks.himmat.shared.idempotency.repository.BusinessEventRepository businessEventRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private PurchaseLineRepository purchaseLineRepository;

    @Autowired
    private SupplierPaymentRepository supplierPaymentRepository;

    @Autowired
    private SupplierLedgerEntryRepository supplierLedgerEntryRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private AppUser ownerUser;
    private AppUser managerUser;
    private String ownerToken;
    private String managerToken;

    private Supplier testSupplier;
    private Brand testBrand;
    private Category testCategory;
    private Subcategory testSubcategory;
    private Product testProduct;
    private ProductVariant variant1;
    private ProductVariant variant2;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE supplier_ledger_entry, payment_allocation, payment_component, supplier_refund, purchase_return_line, purchase_return, supplier_payment, purchase_line, purchase, stock_movement, inventory_balance, product_variant, product, subcategory, category, brand, supplier, business_event, app_user CASCADE");

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

        // 2. Setup Supplier
        testSupplier = supplierRepository.save(Supplier.builder()
                .supplierCode("SUPP-00001")
                .name("Asian Paints Depot")
                .normalizedName("asian paints depot")
                .phone("9876543210")
                .gstin("27AAACA1234A1Z5")
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
                .purchaseDiscountPct(new BigDecimal("10.0000"))
                .salesDiscountPct(BigDecimal.ZERO)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        testSubcategory = subcategoryRepository.save(Subcategory.builder()
                .category(testCategory)
                .name("Emulsion")
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        testProduct = productRepository.save(Product.builder()
                .brand(testBrand)
                .subcategory(testSubcategory)
                .name("Royale Luxury Emulsion")
                .description("Interior paint")
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        variant1 = productVariantRepository.save(ProductVariant.builder()
                .product(testProduct)
                .sku("ROYALE-1L")
                .sizeLabel("1 Litre")
                .pricingMethod(PricingMethod.MRP_DISCOUNT)
                .mrpInclTax(new BigDecimal("650.00"))
                .purchaseCostExclTax(new BigDecimal("400.0000"))
                .gstRatePct(new BigDecimal("18.0000"))
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        inventoryBalanceRepository.save(InventoryBalance.builder()
                .variantId(variant1.getId())
                .variant(variant1)
                .quantityOnHand(0)
                .openingConfirmed(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        variant2 = productVariantRepository.save(ProductVariant.builder()
                .product(testProduct)
                .sku("ROYALE-4L")
                .sizeLabel("4 Litre")
                .pricingMethod(PricingMethod.MRP_DISCOUNT)
                .mrpInclTax(new BigDecimal("2400.00"))
                .purchaseCostExclTax(new BigDecimal("1500.0000"))
                .gstRatePct(new BigDecimal("18.0000"))
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        inventoryBalanceRepository.save(InventoryBalance.builder()
                .variantId(variant2.getId())
                .variant(variant2)
                .quantityOnHand(0)
                .openingConfirmed(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    @Test
    @DisplayName("BE04B-01: Create purchase invoice GST_EXCLUSIVE increases stock and posts positive ledger entry")
    void testCreatePurchaseInvoice_GstExclusive_IncreasesStockAndLedger() throws Exception {
        // Line: 10 units @ 500 entered price, discount null (defaults to category 10%), GST 18%
        // Unit discount = 50. Discounted price excl tax = 450. Tax per unit = 81. Gross unit cost = 531. Line total = 5310.
        PurchaseLineRequest lineReq = PurchaseLineRequest.builder()
                .variantId(variant1.getId())
                .quantity(10)
                .priceBasis(PriceBasis.GST_EXCLUSIVE)
                .enteredUnitPrice(new BigDecimal("500.00"))
                .gstRatePct(new BigDecimal("18.00"))
                .build();

        CreatePurchaseRequest request = CreatePurchaseRequest.builder()
                .supplierId(testSupplier.getId())
                .supplierInvoiceNumber("INV-2026-001")
                .purchaseDate(LocalDate.now())
                .notes("Standard restock")
                .lines(List.of(lineReq))
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.purchaseNumber", containsString("PO-")))
                .andExpect(jsonPath("$.data.supplierName", is("Asian Paints Depot")))
                .andExpect(jsonPath("$.data.totalPayableAmount", is("5310.00")))
                .andExpect(jsonPath("$.data.lines", hasSize(1)))
                .andExpect(jsonPath("$.data.lines[0].sku", is("ROYALE-1L")))
                .andExpect(jsonPath("$.data.lines[0].discountPct", is("10.0000")))
                .andExpect(jsonPath("$.data.lines[0].lineTotal", is("5310.00")))
                .andReturn();

        // 1. Verify Stock increased from 0 to 10
        InventoryBalance ib = inventoryBalanceRepository.findById(variant1.getId()).orElseThrow();
        assertThat(ib.getQuantityOnHand()).isEqualTo(10);

        // 2. Verify Stock Movement recorded
        List<StockMovement> movements = stockMovementRepository.searchMovements(variant1.getId(), null, null, null, null, null, null, org.springframework.data.domain.Pageable.unpaged()).getContent();
        assertThat(movements).hasSize(1);
        StockMovement mv = movements.get(0);
        assertThat(mv.getMovementType()).isEqualTo(StockMovementType.PURCHASE_RECEIPT);
        assertThat(mv.getDirection()).isEqualTo(StockMovementDirection.IN);
        assertThat(mv.getQuantity()).isEqualTo(10);
        assertThat(mv.getQuantityBefore()).isEqualTo(0);
        assertThat(mv.getQuantityAfter()).isEqualTo(10);

        // 3. Verify Supplier Ledger entry recorded (+5310.00, shop owes supplier)
        List<SupplierLedgerEntry> ledgerEntries = supplierLedgerEntryRepository.findAll();
        assertThat(ledgerEntries).hasSize(1);
        SupplierLedgerEntry entry = ledgerEntries.get(0);
        assertThat(entry.getSourceType()).isEqualTo(SupplierLedgerSourceType.PURCHASE);
        assertThat(entry.getAmount()).isEqualByComparingTo("5310.00");
        assertThat(entry.getRunningBalance()).isEqualByComparingTo("5310.00");
    }

    @Test
    @DisplayName("BE04B-02: Purchase with GST_INCLUSIVE correctly derives net cost and total")
    void testCreatePurchaseInvoice_GstInclusive() throws Exception {
        // 5 units @ 1180 entered price inclusive of 18% GST.
        // Gross cost = 1180. Discount = 0%. Net cost excl tax = 1000. Tax = 180. Line total = 5900.
        PurchaseLineRequest lineReq = PurchaseLineRequest.builder()
                .variantId(variant2.getId())
                .quantity(5)
                .priceBasis(PriceBasis.GST_INCLUSIVE)
                .enteredUnitPrice(new BigDecimal("1180.00"))
                .discountPct(BigDecimal.ZERO)
                .gstRatePct(new BigDecimal("18.00"))
                .build();

        CreatePurchaseRequest request = CreatePurchaseRequest.builder()
                .supplierId(testSupplier.getId())
                .lines(List.of(lineReq))
                .build();

        mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalPayableAmount", is("5900.00")))
                .andExpect(jsonPath("$.data.lines[0].netUnitCost", is("1000.0000")))
                .andExpect(jsonPath("$.data.lines[0].grossUnitCost", is("1180.0000")))
                .andExpect(jsonPath("$.data.lines[0].lineTotal", is("5900.00")));

        InventoryBalance ib = inventoryBalanceRepository.findById(variant2.getId()).orElseThrow();
        assertThat(ib.getQuantityOnHand()).isEqualTo(5);
    }

    @Test
    @DisplayName("BE04B-03: Opt-in product cost update updates variant purchase cost")
    void testPurchase_OptInProductCostUpdate() throws Exception {
        PurchaseLineRequest lineReq = PurchaseLineRequest.builder()
                .variantId(variant1.getId())
                .quantity(2)
                .priceBasis(PriceBasis.GST_EXCLUSIVE)
                .enteredUnitPrice(new BigDecimal("420.00"))
                .discountPct(BigDecimal.ZERO)
                .gstRatePct(new BigDecimal("18.00"))
                .updateProductCost(true)
                .variantVersion(variant1.getVersion())
                .build();

        CreatePurchaseRequest request = CreatePurchaseRequest.builder()
                .supplierId(testSupplier.getId())
                .lines(List.of(lineReq))
                .build();

        mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lines[0].updateProductCost", is(true)));

        ProductVariant updatedVariant = productVariantRepository.findById(variant1.getId()).orElseThrow();
        assertThat(updatedVariant.getPurchaseCostExclTax()).isEqualByComparingTo("420.0000");
    }

    @Test
    @DisplayName("BE04B-04: Opt-in product cost update rejected if GST_INCLUSIVE has null GST rate")
    void testPurchase_OptInProductCostUpdate_RejectedWhenGstNullOnInclusive() throws Exception {
        PurchaseLineRequest lineReq = PurchaseLineRequest.builder()
                .variantId(variant1.getId())
                .quantity(2)
                .priceBasis(PriceBasis.GST_INCLUSIVE)
                .enteredUnitPrice(new BigDecimal("500.00"))
                .discountPct(BigDecimal.ZERO)
                .gstRatePct(null) // null GST on inclusive price
                .updateProductCost(true) // Cannot derive canonical cost excl tax
                .build();

        CreatePurchaseRequest request = CreatePurchaseRequest.builder()
                .supplierId(testSupplier.getId())
                .lines(List.of(lineReq))
                .build();

        mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("GST_RATE_REQUIRED_FOR_COST_UPDATE")));
    }

    @Test
    @DisplayName("BE04B-05: The Canonical Prompt Workflow: 1,000 Purchase -> 600 Payment -> 500 Return -> 100 Refund IN")
    void testCanonicalLedgerWorkflow_1000Purchase_600Payment_500Return_100Refund() throws Exception {
        // Step 1: Purchase 10 units @ 100 gross = ₹1,000.00
        PurchaseLineRequest pLine = PurchaseLineRequest.builder()
                .variantId(variant1.getId())
                .quantity(10)
                .priceBasis(PriceBasis.GST_INCLUSIVE)
                .enteredUnitPrice(new BigDecimal("100.00"))
                .discountPct(BigDecimal.ZERO)
                .gstRatePct(new BigDecimal("18.00"))
                .build();

        CreatePurchaseRequest pReq = CreatePurchaseRequest.builder()
                .supplierId(testSupplier.getId())
                .purchaseDate(LocalDate.now())
                .lines(List.of(pLine))
                .build();

        MvcResult pRes = mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalPayableAmount", is("1000.00")))
                .andReturn();

        JsonNode pJson = objectMapper.readTree(pRes.getResponse().getContentAsString());
        long purchaseId = pJson.get("data").get("id").asLong();
        long pLineId = pJson.get("data").get("lines").get(0).get("id").asLong();

        // Check Ledger 1: Balance = +1000.00
        SupplierLedgerEntry entry1 = supplierLedgerEntryRepository.findFirstBySupplierIdOrderByIdDesc(testSupplier.getId()).orElseThrow();
        assertThat(entry1.getAmount()).isEqualByComparingTo("1000.00");
        assertThat(entry1.getRunningBalance()).isEqualByComparingTo("1000.00");
        assertThat(inventoryBalanceRepository.findById(variant1.getId()).orElseThrow().getQuantityOnHand()).isEqualTo(10);

        // Step 2: Payment ₹600.00 allocated to Purchase 1
        CreateSupplierPaymentRequest payReq = CreateSupplierPaymentRequest.builder()
                .paymentDate(LocalDate.now())
                .totalAmount(new BigDecimal("600.00"))
                .components(List.of(
                        PaymentComponentDto.builder().method(PaymentMethod.CASH).amount(new BigDecimal("200.00")).build(),
                        PaymentComponentDto.builder().method(PaymentMethod.ONLINE).amount(new BigDecimal("400.00")).onlineReference("UPI-1234").build()
                ))
                .allocations(List.of(
                        PaymentAllocationRequest.builder().purchaseId(purchaseId).amount(new BigDecimal("600.00")).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/suppliers/" + testSupplier.getId() + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalAmount", is("600.00")))
                .andExpect(jsonPath("$.data.unallocatedAmount", is("0.00")));

        // Check Ledger 2: Balance = 1000 - 600 = +400.00 (Shop owes 400)
        SupplierLedgerEntry entry2 = supplierLedgerEntryRepository.findFirstBySupplierIdOrderByIdDesc(testSupplier.getId()).orElseThrow();
        assertThat(entry2.getAmount()).isEqualByComparingTo("-600.00");
        assertThat(entry2.getRunningBalance()).isEqualByComparingTo("400.00");

        // Purchase allocated amount is now 600
        Purchase purchaseAfterPay = purchaseRepository.findById(purchaseId).orElseThrow();
        assertThat(purchaseAfterPay.getTotalAllocatedAmount()).isEqualByComparingTo("600.00");

        // Step 3: Purchase Return 5 units @ 100 = ₹500.00
        CreatePurchaseReturnRequest retReq = CreatePurchaseReturnRequest.builder()
                .returnDate(LocalDate.now())
                .reason("Defective packaging")
                .lines(List.of(
                        PurchaseReturnLineRequest.builder().purchaseLineId(pLineId).quantity(5).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/purchases/" + purchaseId + "/returns")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(retReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalReturnAmount", is("500.00")));

        // Check Ledger 3: Balance = 400 - 500 = -100.00 (Supplier owes shop 100 / advance)
        SupplierLedgerEntry entry3 = supplierLedgerEntryRepository.findFirstBySupplierIdOrderByIdDesc(testSupplier.getId()).orElseThrow();
        assertThat(entry3.getAmount()).isEqualByComparingTo("-500.00");
        assertThat(entry3.getRunningBalance()).isEqualByComparingTo("-100.00");

        // Stock decreased by 5: 10 - 5 = 5
        assertThat(inventoryBalanceRepository.findById(variant1.getId()).orElseThrow().getQuantityOnHand()).isEqualTo(5);

        // Net payable remaining is 1000 - 500 = 500.
        // Previously allocated was 600, so excess 100 was released back to payment unallocated credit!
        Purchase purchaseAfterReturn = purchaseRepository.findById(purchaseId).orElseThrow();
        assertThat(purchaseAfterReturn.getTotalAllocatedAmount()).isEqualByComparingTo("500.00");

        // Step 4: Supplier Refund IN ₹100.00 (settles balance)
        CreateSupplierRefundRequest refReq = CreateSupplierRefundRequest.builder()
                .refundDate(LocalDate.now())
                .totalAmount(new BigDecimal("100.00"))
                .reason("Refund for returned goods")
                .components(List.of(
                        PaymentComponentDto.builder().method(PaymentMethod.CASH).amount(new BigDecimal("100.00")).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/suppliers/" + testSupplier.getId() + "/refunds")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalAmount", is("100.00")));

        // Check Ledger 4: Balance = -100 + 100 = 0.00 (Settled)
        SupplierLedgerEntry entry4 = supplierLedgerEntryRepository.findFirstBySupplierIdOrderByIdDesc(testSupplier.getId()).orElseThrow();
        assertThat(entry4.getAmount()).isEqualByComparingTo("100.00");
        assertThat(entry4.getRunningBalance()).isEqualByComparingTo("0.00");

        // Step 5: Check Account Summary API
        mockMvc.perform(get("/api/v1/suppliers/" + testSupplier.getId() + "/account-summary")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBalance", is("0.00")))
                .andExpect(jsonPath("$.data.totalPurchases", is("1000.00")))
                .andExpect(jsonPath("$.data.totalPaid", is("600.00")))
                .andExpect(jsonPath("$.data.totalReturned", is("500.00")))
                .andExpect(jsonPath("$.data.totalRefunded", is("100.00")));
    }

    @Test
    @DisplayName("BE04B-06: Strict Non-Negative stock invariant on purchase returns")
    void testPurchaseReturn_StrictNonNegativeStockPreventsExcessReturn() throws Exception {
        // Step 1: Purchase 5 units
        PurchaseLineRequest pLine = PurchaseLineRequest.builder()
                .variantId(variant1.getId())
                .quantity(5)
                .priceBasis(PriceBasis.GST_INCLUSIVE)
                .enteredUnitPrice(new BigDecimal("100.00"))
                .gstRatePct(new BigDecimal("18.00"))
                .build();

        CreatePurchaseRequest pReq = CreatePurchaseRequest.builder()
                .supplierId(testSupplier.getId())
                .lines(List.of(pLine))
                .build();

        MvcResult pRes = mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pReq)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode pJson = objectMapper.readTree(pRes.getResponse().getContentAsString());
        long purchaseId = pJson.get("data").get("id").asLong();
        long pLineId = pJson.get("data").get("lines").get(0).get("id").asLong();

        // Artificially reduce stock to 2 units (simulate sales in inventory)
        InventoryBalance ib = inventoryBalanceRepository.findById(variant1.getId()).orElseThrow();
        ib.setQuantityOnHand(2);
        inventoryBalanceRepository.save(ib);

        // Attempt to return 5 units when only 2 are on hand
        CreatePurchaseReturnRequest retReq = CreatePurchaseReturnRequest.builder()
                .returnDate(LocalDate.now())
                .reason("Excess defect return")
                .lines(List.of(
                        PurchaseReturnLineRequest.builder().purchaseLineId(pLineId).quantity(5).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/purchases/" + purchaseId + "/returns")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(retReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("INSUFFICIENT_STOCK")));

        // Stock unchanged at 2
        assertThat(inventoryBalanceRepository.findById(variant1.getId()).orElseThrow().getQuantityOnHand()).isEqualTo(2);
    }

    @Test
    @DisplayName("BE04B-07: Cumulative return quantity cannot exceed purchased line quantity")
    void testPurchaseReturn_CumulativeQuantityCannotExceedPurchased() throws Exception {
        PurchaseLineRequest pLine = PurchaseLineRequest.builder()
                .variantId(variant1.getId())
                .quantity(5)
                .priceBasis(PriceBasis.GST_INCLUSIVE)
                .enteredUnitPrice(new BigDecimal("100.00"))
                .gstRatePct(new BigDecimal("18.00"))
                .build();

        CreatePurchaseRequest pReq = CreatePurchaseRequest.builder()
                .supplierId(testSupplier.getId())
                .lines(List.of(pLine))
                .build();

        MvcResult pRes = mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pReq)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode pJson = objectMapper.readTree(pRes.getResponse().getContentAsString());
        long purchaseId = pJson.get("data").get("id").asLong();
        long pLineId = pJson.get("data").get("lines").get(0).get("id").asLong();

        // Attempt to return 6 units when only 5 were purchased
        CreatePurchaseReturnRequest retReq = CreatePurchaseReturnRequest.builder()
                .returnDate(LocalDate.now())
                .reason("Over-return")
                .lines(List.of(
                        PurchaseReturnLineRequest.builder().purchaseLineId(pLineId).quantity(6).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/purchases/" + purchaseId + "/returns")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(retReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("EXCESS_RETURN_QUANTITY")));
    }

    @Test
    @DisplayName("BE04B-08: Supplier payment unallocated amount becomes advance and can be allocated later")
    void testSupplierPayment_AdvanceAndAllocation() throws Exception {
        // Step 1: Make an unallocated payment of ₹1,000.00 (advance)
        CreateSupplierPaymentRequest payReq = CreateSupplierPaymentRequest.builder()
                .totalAmount(new BigDecimal("1000.00"))
                .components(List.of(
                        PaymentComponentDto.builder().method(PaymentMethod.ONLINE).amount(new BigDecimal("1000.00")).onlineReference("ADV-101").build()
                ))
                .build();

        MvcResult payRes = mockMvc.perform(post("/api/v1/suppliers/" + testSupplier.getId() + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.unallocatedAmount", is("1000.00")))
                .andReturn();

        long paymentId = objectMapper.readTree(payRes.getResponse().getContentAsString()).get("data").get("id").asLong();

        // Ledger: -1000.00 (Supplier holds shop advance)
        SupplierLedgerEntry entry1 = supplierLedgerEntryRepository.findFirstBySupplierIdOrderByIdDesc(testSupplier.getId()).orElseThrow();
        assertThat(entry1.getRunningBalance()).isEqualByComparingTo("-1000.00");

        // Step 2: Create a purchase of ₹700.00
        PurchaseLineRequest pLine = PurchaseLineRequest.builder()
                .variantId(variant1.getId())
                .quantity(7)
                .priceBasis(PriceBasis.GST_INCLUSIVE)
                .enteredUnitPrice(new BigDecimal("100.00"))
                .discountPct(BigDecimal.ZERO)
                .gstRatePct(new BigDecimal("18.00"))
                .build();

        CreatePurchaseRequest pReq = CreatePurchaseRequest.builder()
                .supplierId(testSupplier.getId())
                .lines(List.of(pLine))
                .build();

        MvcResult pRes = mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalPayableAmount", is("700.00")))
                .andReturn();

        long purchaseId = objectMapper.readTree(pRes.getResponse().getContentAsString()).get("data").get("id").asLong();

        // Ledger: -1000 + 700 = -300.00
        SupplierLedgerEntry entry2 = supplierLedgerEntryRepository.findFirstBySupplierIdOrderByIdDesc(testSupplier.getId()).orElseThrow();
        assertThat(entry2.getRunningBalance()).isEqualByComparingTo("-300.00");

        // Step 3: Apply ₹700.00 advance from payment to purchase
        ApplySupplierAllocationRequest allocReq = ApplySupplierAllocationRequest.builder()
                .allocations(List.of(
                        AllocationItemRequest.builder().paymentId(paymentId).purchaseId(purchaseId).amount(new BigDecimal("700.00")).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/suppliers/" + testSupplier.getId() + "/allocations")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(allocReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].allocatedAmount", is("700.00")));

        // Verify purchase is now fully allocated
        Purchase updatedPurchase = purchaseRepository.findById(purchaseId).orElseThrow();
        assertThat(updatedPurchase.getTotalAllocatedAmount()).isEqualByComparingTo("700.00");

        // Verify payment now has 300 unallocated
        assertThat(supplierPaymentRepository.findById(paymentId).orElseThrow().getUnallocatedAmount()).isEqualByComparingTo("300.00");

        // Verify NO extra ledger entry was posted by the internal allocation
        assertThat(supplierLedgerEntryRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("BE04B-09: Supplier Ledger Statement date range query with opening and closing balances")
    void testSupplierLedgerStatement_DateRangeAndBalances() throws Exception {
        // Entry 1: 5 days ago (simulated in DB)
        Purchase p = purchaseRepository.save(Purchase.builder()
                .purchaseNumber("PO-00001")
                .supplier(testSupplier)
                .purchaseDate(LocalDate.now().minusDays(5))
                .status(com.asalkartechworks.himmat.purchase.model.PurchaseStatus.POSTED)
                .supplierName(testSupplier.getName())
                .subtotalExclTax(new BigDecimal("1000.00"))
                .totalDiscountAmount(BigDecimal.ZERO)
                .totalPayableAmount(new BigDecimal("1000.00"))
                .totalAllocatedAmount(BigDecimal.ZERO)
                .businessEvent(businessEventRepository.save(com.asalkartechworks.himmat.shared.idempotency.model.BusinessEvent.builder()
                        .eventType("PURCHASE").requestKey("K1").requestHash("H1").actorId(ownerUser.getId()).occurredAt(Instant.now()).auditPayload("{}").build()))
                .createdAt(Instant.now().minusSeconds(86400 * 5))
                .updatedAt(Instant.now().minusSeconds(86400 * 5))
                .build());

        supplierLedgerEntryRepository.save(SupplierLedgerEntry.builder()
                .supplier(testSupplier)
                .businessEvent(p.getBusinessEvent())
                .sourceType(SupplierLedgerSourceType.PURCHASE)
                .sourceId(p.getId())
                .amount(new BigDecimal("1000.00"))
                .runningBalance(new BigDecimal("1000.00"))
                .effectiveDate(LocalDate.now().minusDays(5))
                .createdAt(Instant.now().minusSeconds(86400 * 5))
                .build());

        // Entry 2: Today
        supplierLedgerEntryRepository.save(SupplierLedgerEntry.builder()
                .supplier(testSupplier)
                .businessEvent(p.getBusinessEvent())
                .sourceType(SupplierLedgerSourceType.PAYMENT)
                .sourceId(99L)
                .amount(new BigDecimal("-400.00"))
                .runningBalance(new BigDecimal("600.00"))
                .effectiveDate(LocalDate.now())
                .createdAt(Instant.now())
                .build());

        // Query statement for [today, today]
        mockMvc.perform(get("/api/v1/suppliers/" + testSupplier.getId() + "/ledger")
                        .param("startDate", LocalDate.now().toString())
                        .param("endDate", LocalDate.now().toString())
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openingBalance", is("1000.00")))
                .andExpect(jsonPath("$.data.closingBalance", is("600.00")))
                .andExpect(jsonPath("$.data.entries.data", hasSize(1)))
                .andExpect(jsonPath("$.data.entries.data[0].amount", is("-400.00")));
    }

    @Test
    @DisplayName("BE04B-10: Idempotency-Key guarantees safe replay on duplicate purchase request")
    void testPurchaseCreation_IdempotencyReplay() throws Exception {
        String idemKey = "test-idem-" + UUID.randomUUID();

        PurchaseLineRequest lineReq = PurchaseLineRequest.builder()
                .variantId(variant1.getId())
                .quantity(3)
                .priceBasis(PriceBasis.GST_INCLUSIVE)
                .enteredUnitPrice(new BigDecimal("200.00"))
                .gstRatePct(new BigDecimal("18.00"))
                .build();

        CreatePurchaseRequest request = CreatePurchaseRequest.builder()
                .supplierId(testSupplier.getId())
                .lines(List.of(lineReq))
                .build();

        // Request 1
        MvcResult res1 = mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String body1 = res1.getResponse().getContentAsString();
        long purchaseId1 = objectMapper.readTree(body1).get("data").get("id").asLong();

        // Request 2 with exact same Idempotency-Key
        MvcResult res2 = mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String body2 = res2.getResponse().getContentAsString();
        long purchaseId2 = objectMapper.readTree(body2).get("data").get("id").asLong();

        // Verifies same purchase ID replayed, no duplicate stock movement, no duplicate ledger entry
        assertThat(purchaseId1).isEqualTo(purchaseId2);
        assertThat(purchaseRepository.count()).isEqualTo(1);
        assertThat(inventoryBalanceRepository.findById(variant1.getId()).orElseThrow().getQuantityOnHand()).isEqualTo(3);
        assertThat(supplierLedgerEntryRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("BE04B-11: Conflicting payload with same Idempotency-Key returns 409 Conflict")
    void testPurchaseCreation_IdempotencyConflict() throws Exception {
        String idemKey = "test-conflict-" + UUID.randomUUID();

        PurchaseLineRequest lineReq = PurchaseLineRequest.builder()
                .variantId(variant1.getId())
                .quantity(2)
                .priceBasis(PriceBasis.GST_INCLUSIVE)
                .enteredUnitPrice(new BigDecimal("100.00"))
                .gstRatePct(new BigDecimal("18.00"))
                .build();

        CreatePurchaseRequest request1 = CreatePurchaseRequest.builder()
                .supplierId(testSupplier.getId())
                .lines(List.of(lineReq))
                .build();

        mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // Same key, different quantity
        lineReq.setQuantity(4);
        CreatePurchaseRequest request2 = CreatePurchaseRequest.builder()
                .supplierId(testSupplier.getId())
                .lines(List.of(lineReq))
                .build();

        mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("IDEMPOTENCY_CONFLICT")));
    }

    @Test
    @DisplayName("BE04B-12: Unauthenticated request to purchase endpoints returns 401 Unauthorized")
    void testPurchaseEndpoints_UnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/purchases"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("BE04B-13: Inactive supplier rejected for purchase creation")
    void testCreatePurchase_InactiveSupplierRejected() throws Exception {
        testSupplier.setActive(false);
        supplierRepository.save(testSupplier);

        PurchaseLineRequest lineReq = PurchaseLineRequest.builder()
                .variantId(variant1.getId())
                .quantity(1)
                .priceBasis(PriceBasis.GST_INCLUSIVE)
                .enteredUnitPrice(new BigDecimal("100.00"))
                .gstRatePct(new BigDecimal("18.00"))
                .build();

        CreatePurchaseRequest request = CreatePurchaseRequest.builder()
                .supplierId(testSupplier.getId())
                .lines(List.of(lineReq))
                .build();

        mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("INACTIVE_SUPPLIER")));
    }
}
