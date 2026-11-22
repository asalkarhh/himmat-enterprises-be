package com.asalkartechworks.himmat.inventory;

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
import com.asalkartechworks.himmat.inventory.dto.CreateManualAdjustmentRequest;
import com.asalkartechworks.himmat.inventory.dto.ManualAdjustmentLineRequest;
import com.asalkartechworks.himmat.inventory.model.StockMovement;
import com.asalkartechworks.himmat.inventory.model.StockMovementDirection;
import com.asalkartechworks.himmat.inventory.model.StockMovementType;
import com.asalkartechworks.himmat.inventory.repository.StockMovementRepository;
import com.asalkartechworks.himmat.inventory.service.StockPostingLine;
import com.asalkartechworks.himmat.inventory.service.StockPostingRequest;
import com.asalkartechworks.himmat.inventory.service.StockPostingResult;
import com.asalkartechworks.himmat.inventory.service.StockPostingService;
import com.asalkartechworks.himmat.security.JwtService;
import com.asalkartechworks.himmat.security.UserPrincipal;
import com.asalkartechworks.himmat.shared.idempotency.model.BusinessEvent;
import com.asalkartechworks.himmat.shared.idempotency.repository.BusinessEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private BusinessEventRepository businessEventRepository;

    @Autowired
    private StockPostingService stockPostingService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private AppUser ownerUser;
    private AppUser managerUser;
    private String ownerToken;
    private String managerToken;

    private Brand testBrand;
    private Category testCategory;
    private Subcategory testSubcategory;
    private Product testProduct;
    private ProductVariant variant1;
    private ProductVariant variant2;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE stock_movement, inventory_balance, product_variant, product, subcategory, category, brand, business_event, app_user CASCADE");

        Instant now = Instant.now();

        // 1. Setup Users
        ownerUser = AppUser.builder()
                .username("owner_" + UUID.randomUUID().toString().substring(0, 8))
                .displayName("Shop Owner")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(UserRole.OWNER)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();
        ownerUser = appUserRepository.save(ownerUser);

        managerUser = AppUser.builder()
                .username("manager_" + UUID.randomUUID().toString().substring(0, 8))
                .displayName("Shop Manager")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(UserRole.MANAGER)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();
        managerUser = appUserRepository.save(managerUser);

        ownerToken = "Bearer " + jwtService.generateToken(UserPrincipal.create(ownerUser));
        managerToken = "Bearer " + jwtService.generateToken(UserPrincipal.create(managerUser));

        // 2. Setup Catalog Hierarchy
        testBrand = brandRepository.save(Brand.builder()
                .name("Asian Paints")
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        testCategory = categoryRepository.save(Category.builder()
                .name("Paints")
                .purchaseDiscountPct(BigDecimal.ZERO)
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

        // 3. Setup Product Variants and 1-to-1 Inventory Balances
        variant1 = productVariantRepository.save(ProductVariant.builder()
                .product(testProduct)
                .sku("ROYALE-1L")
                .sizeLabel("1 Litre")
                .pricingMethod(PricingMethod.MRP_DISCOUNT)
                .mrpInclTax(new BigDecimal("550.00"))
                .lowStockThreshold(5)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        inventoryBalanceRepository.save(InventoryBalance.builder()
                .variantId(variant1.getId())
                .variant(variant1)
                .quantityOnHand(0)
                .openingConfirmed(false)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        variant2 = productVariantRepository.save(ProductVariant.builder()
                .product(testProduct)
                .sku("ROYALE-4L")
                .sizeLabel("4 Litre")
                .pricingMethod(PricingMethod.MRP_DISCOUNT)
                .mrpInclTax(new BigDecimal("2100.00"))
                .lowStockThreshold(2)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        inventoryBalanceRepository.save(InventoryBalance.builder()
                .variantId(variant2.getId())
                .variant(variant2)
                .quantityOnHand(0)
                .openingConfirmed(false)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    // ==========================================
    // 1. Inventory Balance & Structure Tests
    // ==========================================

    @Test
    @DisplayName("Product variant has exactly one inventory balance initialized to zero")
    void variantHasExactlyOneInventoryBalance() {
        InventoryBalance balance = inventoryBalanceRepository.findByVariantId(variant1.getId()).orElseThrow();
        assertThat(balance.getQuantityOnHand()).isEqualTo(0);
        assertThat(balance.getVersion()).isEqualTo(0L);
        assertThat(balance.isOpeningConfirmed()).isFalse();
    }

    // ==========================================
    // 2. Manual Stock Adjustment Tests
    // ==========================================

    @Test
    @DisplayName("Manual IN adjustment increases stock and creates immutable StockMovement record")
    void manualAdjustment_in_increasesStockAndAppendsMovement() throws Exception {
        CreateManualAdjustmentRequest request = CreateManualAdjustmentRequest.builder()
                .reason("Initial verified floor stock count")
                .lines(List.of(
                        ManualAdjustmentLineRequest.builder()
                                .variantId(variant1.getId())
                                .direction(StockMovementDirection.IN)
                                .quantity(15)
                                .expectedVersion(0L)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reason").value("Initial verified floor stock count"))
                .andExpect(jsonPath("$.data.lines", hasSize(1)))
                .andExpect(jsonPath("$.data.lines[0].variantId").value(variant1.getId().toString()))
                .andExpect(jsonPath("$.data.lines[0].direction").value("IN"))
                .andExpect(jsonPath("$.data.lines[0].quantity").value(15))
                .andExpect(jsonPath("$.data.lines[0].previousQuantityOnHand").value(0))
                .andExpect(jsonPath("$.data.lines[0].newQuantityOnHand").value(15))
                .andExpect(jsonPath("$.data.lines[0].previousVersion").value(0))
                .andExpect(jsonPath("$.data.lines[0].newVersion").value(1));

        InventoryBalance balance = inventoryBalanceRepository.findByVariantId(variant1.getId()).orElseThrow();
        assertThat(balance.getQuantityOnHand()).isEqualTo(15);
        assertThat(balance.getVersion()).isEqualTo(1L);

        List<StockMovement> movements = stockMovementRepository.findAll();
        assertThat(movements).hasSize(1);
        StockMovement m = movements.getFirst();
        assertThat(m.getMovementType()).isEqualTo(StockMovementType.MANUAL_ADJUSTMENT_IN);
        assertThat(m.getDirection()).isEqualTo(StockMovementDirection.IN);
        assertThat(m.getQuantity()).isEqualTo(15);
        assertThat(m.getQuantityBefore()).isEqualTo(0);
        assertThat(m.getQuantityAfter()).isEqualTo(15);
        assertThat(m.getSourceEntityType()).isEqualTo("MANUAL_ADJUSTMENT");
    }

    @Test
    @DisplayName("Manual OUT adjustment decreases stock correctly when sufficient stock is available")
    void manualAdjustment_out_decreasesStock() throws Exception {
        // Pre-set stock to 20
        InventoryBalance b = inventoryBalanceRepository.findByVariantId(variant1.getId()).orElseThrow();
        b.setQuantityOnHand(20);
        b = inventoryBalanceRepository.save(b);

        CreateManualAdjustmentRequest request = CreateManualAdjustmentRequest.builder()
                .reason("Damaged container disposal")
                .lines(List.of(
                        ManualAdjustmentLineRequest.builder()
                                .variantId(variant1.getId())
                                .direction(StockMovementDirection.OUT)
                                .quantity(4)
                                .expectedVersion(b.getVersion())
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lines[0].previousQuantityOnHand").value(20))
                .andExpect(jsonPath("$.data.lines[0].newQuantityOnHand").value(16))
                .andExpect(jsonPath("$.data.lines[0].direction").value("OUT"));

        InventoryBalance updated = inventoryBalanceRepository.findByVariantId(variant1.getId()).orElseThrow();
        assertThat(updated.getQuantityOnHand()).isEqualTo(16);
    }

    @Test
    @DisplayName("Manual OUT adjustment exceeding current stock is rejected with INSUFFICIENT_STOCK")
    void manualAdjustment_outExceedingStock_rejectedWithInsufficientStock() throws Exception {
        // Stock is 0
        CreateManualAdjustmentRequest request = CreateManualAdjustmentRequest.builder()
                .reason("Attempting to deduct without stock")
                .lines(List.of(
                        ManualAdjustmentLineRequest.builder()
                                .variantId(variant1.getId())
                                .direction(StockMovementDirection.OUT)
                                .quantity(5)
                                .expectedVersion(0L)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_STOCK"));

        // Verify no movements and stock unchanged
        assertThat(stockMovementRepository.findAll()).isEmpty();
        assertThat(inventoryBalanceRepository.findByVariantId(variant1.getId()).orElseThrow().getQuantityOnHand()).isEqualTo(0);
    }

    @Test
    @DisplayName("Manual adjustment with stale expectedVersion is rejected with STALE_VERSION")
    void manualAdjustment_staleExpectedVersion_rejectedWithConflict() throws Exception {
        CreateManualAdjustmentRequest request = CreateManualAdjustmentRequest.builder()
                .reason("Stale edit check")
                .lines(List.of(
                        ManualAdjustmentLineRequest.builder()
                                .variantId(variant1.getId())
                                .direction(StockMovementDirection.IN)
                                .quantity(5)
                                .expectedVersion(99L) // Wrong version
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STALE_VERSION"));
    }

    @Test
    @DisplayName("Manual adjustment with duplicate variant lines is rejected with DUPLICATE_VARIANT_LINE")
    void manualAdjustment_duplicateVariantLines_rejected() throws Exception {
        CreateManualAdjustmentRequest request = CreateManualAdjustmentRequest.builder()
                .reason("Duplicate variant attempt")
                .lines(List.of(
                        ManualAdjustmentLineRequest.builder()
                                .variantId(variant1.getId())
                                .direction(StockMovementDirection.IN)
                                .quantity(5)
                                .expectedVersion(0L)
                                .build(),
                        ManualAdjustmentLineRequest.builder()
                                .variantId(variant1.getId())
                                .direction(StockMovementDirection.OUT)
                                .quantity(2)
                                .expectedVersion(0L)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_VARIANT_LINE"));
    }

    @Test
    @DisplayName("Multi-line adjustment rolls back completely if any line fails")
    void manualAdjustment_multiLineFailure_rollsBackAllLines() throws Exception {
        // Variant 1 has stock 0, Variant 2 has stock 0
        CreateManualAdjustmentRequest request = CreateManualAdjustmentRequest.builder()
                .reason("Multi-line atomicity test")
                .lines(List.of(
                        // Line 1: valid IN 10 on variant1
                        ManualAdjustmentLineRequest.builder()
                                .variantId(variant1.getId())
                                .direction(StockMovementDirection.IN)
                                .quantity(10)
                                .expectedVersion(0L)
                                .build(),
                        // Line 2: invalid OUT 5 on variant2 (has 0 stock)
                        ManualAdjustmentLineRequest.builder()
                                .variantId(variant2.getId())
                                .direction(StockMovementDirection.OUT)
                                .quantity(5)
                                .expectedVersion(0L)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_STOCK"));

        // Both variants must remain at 0 stock, no movements created
        assertThat(inventoryBalanceRepository.findByVariantId(variant1.getId()).orElseThrow().getQuantityOnHand()).isEqualTo(0);
        assertThat(inventoryBalanceRepository.findByVariantId(variant2.getId()).orElseThrow().getQuantityOnHand()).isEqualTo(0);
        assertThat(stockMovementRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Manual adjustment rejecting inactive variant or inactive parent product")
    void manualAdjustment_inactiveVariant_rejected() throws Exception {
        variant1.setActive(false);
        productVariantRepository.save(variant1);

        CreateManualAdjustmentRequest request = CreateManualAdjustmentRequest.builder()
                .reason("Adjustment on discontinued variant")
                .lines(List.of(
                        ManualAdjustmentLineRequest.builder()
                                .variantId(variant1.getId())
                                .direction(StockMovementDirection.IN)
                                .quantity(5)
                                .expectedVersion(0L)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("INACTIVE_VARIANT"));
    }

    @Test
    @DisplayName("Historical resolution allows stock posting for inactive variant when explicitly enabled")
    void historicalResolution_allowsInactiveVariantPosting() {
        variant1.setActive(false);
        productVariantRepository.save(variant1);

        transactionTemplate.execute(status -> {
            BusinessEvent event = businessEventRepository.save(BusinessEvent.builder()
                    .eventType("PURCHASE_RETURN")
                    .requestKey("HIST-" + UUID.randomUUID())
                    .requestHash("hash")
                    .actorId(ownerUser.getId())
                    .occurredAt(Instant.now())
                    .auditPayload("{}")
                    .build());

            StockPostingRequest postingRequest = StockPostingRequest.builder()
                    .businessEventId(event.getId())
                    .actorId(ownerUser.getId())
                    .reason("Return to vendor for discontinued item")
                    .sourceEntityType("PURCHASE_RETURN")
                    .sourceEntityId(event.getId())
                    .allowInactiveVariants(true) // historical resolution flag
                    .lines(List.of(
                            StockPostingLine.builder()
                                    .variantId(variant1.getId())
                                    .movementType(StockMovementType.PURCHASE_RECEIPT)
                                    .direction(StockMovementDirection.IN)
                                    .quantity(10)
                                    .build()
                    ))
                    .build();

            StockPostingResult result = stockPostingService.postMovements(postingRequest);
            assertThat(result.getLines()).hasSize(1);
            assertThat(result.getLines().getFirst().getNewQuantityOnHand()).isEqualTo(10);
            return null;
        });

        assertThat(inventoryBalanceRepository.findByVariantId(variant1.getId()).orElseThrow().getQuantityOnHand()).isEqualTo(10);
    }

    // ==========================================
    // 3. Idempotency Tests
    // ==========================================

    @Test
    @DisplayName("Idempotent manual adjustment replays original result on exact retry")
    void idempotentAdjustment_exactRetry_replaysOriginalResponse() throws Exception {
        String idempotencyKey = "key-test-idem-" + UUID.randomUUID();

        CreateManualAdjustmentRequest request = CreateManualAdjustmentRequest.builder()
                .reason("Physical count verification")
                .lines(List.of(
                        ManualAdjustmentLineRequest.builder()
                                .variantId(variant1.getId())
                                .direction(StockMovementDirection.IN)
                                .quantity(12)
                                .expectedVersion(0L)
                                .build()
                ))
                .build();

        // 1st invocation
        MvcResult firstResult = mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lines[0].newQuantityOnHand").value(12))
                .andReturn();

        // Stock is now 12, version is 1
        assertThat(inventoryBalanceRepository.findByVariantId(variant1.getId()).orElseThrow().getQuantityOnHand()).isEqualTo(12);
        assertThat(stockMovementRepository.findAll()).hasSize(1);

        // 2nd invocation (exact retry with old expectedVersion 0)
        MvcResult secondResult = mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lines[0].newQuantityOnHand").value(12))
                .andReturn();

        // Verify stock was NOT double-incremented and no duplicate movement created
        assertThat(inventoryBalanceRepository.findByVariantId(variant1.getId()).orElseThrow().getQuantityOnHand()).isEqualTo(12);
        assertThat(stockMovementRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Idempotency key reuse with different payload returns 409 IDEMPOTENCY_CONFLICT")
    void idempotentAdjustment_payloadMismatch_returnsConflict() throws Exception {
        String idempotencyKey = "key-conflict-" + UUID.randomUUID();

        CreateManualAdjustmentRequest request1 = CreateManualAdjustmentRequest.builder()
                .reason("Count 1")
                .lines(List.of(
                        ManualAdjustmentLineRequest.builder()
                                .variantId(variant1.getId())
                                .direction(StockMovementDirection.IN)
                                .quantity(5)
                                .expectedVersion(0L)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        CreateManualAdjustmentRequest request2 = CreateManualAdjustmentRequest.builder()
                .reason("Count 2 different payload")
                .lines(List.of(
                        ManualAdjustmentLineRequest.builder()
                                .variantId(variant1.getId())
                                .direction(StockMovementDirection.IN)
                                .quantity(8) // Different quantity!
                                .expectedVersion(0L)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));
    }

    // ==========================================
    // 4. Concurrency & Row-Locking Tests
    // ==========================================

    @Test
    @DisplayName("Concurrent deductions against limited stock: exactly one succeeds and stock never goes negative")
    void concurrentDeductions_againstLimitedStock_serializesCorrectly() throws Exception {
        // Setup initial stock = 5
        InventoryBalance b = inventoryBalanceRepository.findByVariantId(variant1.getId()).orElseThrow();
        b.setQuantityOnHand(5);
        b.setVersion(0L);
        inventoryBalanceRepository.save(b);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger insufficientStockCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // Each thread attempts to deduct 4 pieces
                    transactionTemplate.execute(status -> {
                        BusinessEvent event = businessEventRepository.save(BusinessEvent.builder()
                                .eventType("MANUAL_STOCK_ADJUSTMENT")
                                .requestKey("CONCURRENT-" + UUID.randomUUID())
                                .requestHash("hash")
                                .actorId(ownerUser.getId())
                                .occurredAt(Instant.now())
                                .auditPayload("{}")
                                .build());

                        StockPostingRequest postingRequest = StockPostingRequest.builder()
                                .businessEventId(event.getId())
                                .actorId(ownerUser.getId())
                                .reason("Concurrent deduction")
                                .sourceEntityType("MANUAL_ADJUSTMENT")
                                .sourceEntityId(event.getId())
                                .allowInactiveVariants(false)
                                .lines(List.of(
                                        StockPostingLine.builder()
                                                .variantId(variant1.getId())
                                                .movementType(StockMovementType.MANUAL_ADJUSTMENT_OUT)
                                                .direction(StockMovementDirection.OUT)
                                                .quantity(4)
                                                .build()
                                ))
                                .build();

                        stockPostingService.postMovements(postingRequest);
                        return null;
                    });
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("Insufficient stock")) {
                        insufficientStockCount.incrementAndGet();
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // Exactly one should succeed, one should fail due to insufficient stock
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(insufficientStockCount.get()).isEqualTo(1);

        // Final stock must be exactly 5 - 4 = 1
        InventoryBalance finalBalance = inventoryBalanceRepository.findByVariantId(variant1.getId()).orElseThrow();
        assertThat(finalBalance.getQuantityOnHand()).isEqualTo(1);

        // Only one movement record should be committed
        List<StockMovement> movements = stockMovementRepository.findAll();
        assertThat(movements).hasSize(1);
        assertThat(movements.getFirst().getQuantity()).isEqualTo(4);
        assertThat(movements.getFirst().getQuantityBefore()).isEqualTo(5);
        assertThat(movements.getFirst().getQuantityAfter()).isEqualTo(1);
    }

    // ==========================================
    // 5. Inventory & Movement Read API Tests
    // ==========================================

    @Test
    @DisplayName("GET /api/v1/inventory lists inventory with product hierarchy and filters")
    void getInventory_withFilters_returnsCorrectList() throws Exception {
        // Give variant1 stock 10, variant2 stock 0
        InventoryBalance b1 = inventoryBalanceRepository.findByVariantId(variant1.getId()).orElseThrow();
        b1.setQuantityOnHand(10);
        inventoryBalanceRepository.save(b1);

        // 1. Filter IN_STOCK
        mockMvc.perform(get("/api/v1/inventory")
                        .param("stockStatus", "IN_STOCK")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].sku").value("ROYALE-1L"))
                .andExpect(jsonPath("$.data[0].quantityOnHand").value(10));

        // 2. Filter OUT_OF_STOCK
        mockMvc.perform(get("/api/v1/inventory")
                        .param("stockStatus", "OUT_OF_STOCK")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].sku").value("ROYALE-4L"))
                .andExpect(jsonPath("$.data[0].quantityOnHand").value(0));

        // 3. Search filter
        mockMvc.perform(get("/api/v1/inventory")
                        .param("search", "4 Litre")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].sku").value("ROYALE-4L"));
    }

    @Test
    @DisplayName("GET /api/v1/inventory/variants/{id} returns single variant inventory details")
    void getInventoryByVariantId_returnsDetails() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/variants/" + variant1.getId())
                        .header(HttpHeaders.AUTHORIZATION, managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.variantId").value(variant1.getId().toString()))
                .andExpect(jsonPath("$.data.productName").value("Royale Luxury Emulsion"))
                .andExpect(jsonPath("$.data.brandName").value("Asian Paints"))
                .andExpect(jsonPath("$.data.categoryName").value("Paints"))
                .andExpect(jsonPath("$.data.subcategoryName").value("Emulsion"))
                .andExpect(jsonPath("$.data.sku").value("ROYALE-1L"));
    }

    @Test
    @DisplayName("GET /api/v1/inventory/movements filters movements by variant and date range in Asia/Kolkata")
    void getMovements_withDateRangeAndVariantFilter() throws Exception {
        // Create an IN adjustment
        CreateManualAdjustmentRequest request = CreateManualAdjustmentRequest.builder()
                .reason("Stock take receipt")
                .lines(List.of(
                        ManualAdjustmentLineRequest.builder()
                                .variantId(variant1.getId())
                                .direction(StockMovementDirection.IN)
                                .quantity(8)
                                .expectedVersion(0L)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        LocalDate today = LocalDate.now();

        mockMvc.perform(get("/api/v1/inventory/movements")
                        .param("variantId", variant1.getId().toString())
                        .param("direction", "IN")
                        .param("fromDate", today.toString())
                        .param("toDate", today.toString())
                        .header(HttpHeaders.AUTHORIZATION, managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].sku").value("ROYALE-1L"))
                .andExpect(jsonPath("$.data[0].direction").value("IN"))
                .andExpect(jsonPath("$.data[0].quantity").value(8));

        // Also test scoped variant movements endpoint
        mockMvc.perform(get("/api/v1/inventory/variants/" + variant1.getId() + "/movements")
                        .header(HttpHeaders.AUTHORIZATION, managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @DisplayName("Fractional quantity in request is rejected with 400 Bad Request")
    void manualAdjustment_fractionalQuantity_rejected() throws Exception {
        String json = """
            {
                "reason": "Fractional quantity test",
                "lines": [
                    {
                        "variantId": "%d",
                        "direction": "IN",
                        "quantity": 2.5,
                        "expectedVersion": 0
                    }
                ]
            }
            """.formatted(variant1.getId());

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_JSON"));
    }

    @Test
    @DisplayName("Zero quantity in request line is rejected with 422 Unprocessable Entity")
    void manualAdjustment_zeroQuantity_rejected() throws Exception {
        CreateManualAdjustmentRequest request = CreateManualAdjustmentRequest.builder()
                .reason("Zero quantity test")
                .lines(List.of(
                        ManualAdjustmentLineRequest.builder()
                                .variantId(variant1.getId())
                                .direction(StockMovementDirection.IN)
                                .quantity(0)
                                .expectedVersion(0L)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Blank adjustment reason is rejected with 422 Unprocessable Entity")
    void manualAdjustment_blankReason_rejected() throws Exception {
        CreateManualAdjustmentRequest request = CreateManualAdjustmentRequest.builder()
                .reason("   ")
                .lines(List.of(
                        ManualAdjustmentLineRequest.builder()
                                .variantId(variant1.getId())
                                .direction(StockMovementDirection.IN)
                                .quantity(5)
                                .expectedVersion(0L)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Database check constraint chk_inventory_balance_non_negative prevents negative stock")
    void databaseConstraint_preventsNegativeStock() {
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> {
            jdbcTemplate.execute("UPDATE inventory_balance SET quantity_on_hand = -1 WHERE variant_id = " + variant1.getId());
        });
    }

    @Test
    @DisplayName("Database check constraint chk_stock_movement_arithmetic prevents inconsistent movement math")
    void databaseConstraint_preventsInconsistentMovementMath() {
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> {
            jdbcTemplate.execute("""
                INSERT INTO stock_movement (variant_id, business_event_id, movement_type, direction,
                                            quantity, quantity_before, quantity_after, source_entity_type,
                                            actor_id, occurred_at, created_at)
                VALUES (%d, 1, 'MANUAL_ADJUSTMENT_IN', 'IN', 10, 0, 5, 'MANUAL_ADJUSTMENT', %d, NOW(), NOW())
            """.formatted(variant1.getId(), ownerUser.getId()));
        });
    }

    // ==========================================
    // 6. Security & Authorization Tests
    // ==========================================

    @Test
    @DisplayName("Unauthenticated requests to inventory endpoints are rejected with 401")
    void unauthenticatedAccess_rejectedWith401() throws Exception {
        mockMvc.perform(get("/api/v1/inventory"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/inventory/variants/" + variant1.getId()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/inventory/movements"))
                .andExpect(status().isUnauthorized());

        CreateManualAdjustmentRequest request = CreateManualAdjustmentRequest.builder()
                .reason("Test")
                .lines(List.of(ManualAdjustmentLineRequest.builder()
                        .variantId(variant1.getId())
                        .direction(StockMovementDirection.IN)
                        .quantity(1)
                        .expectedVersion(0L)
                        .build()))
                .build();

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
