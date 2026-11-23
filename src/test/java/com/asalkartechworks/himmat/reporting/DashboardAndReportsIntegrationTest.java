package com.asalkartechworks.himmat.reporting;

import com.asalkartechworks.himmat.billing.dto.CreateCustomerReceiptRequest;
import com.asalkartechworks.himmat.billing.dto.CreateInvoiceRequest;
import com.asalkartechworks.himmat.billing.dto.InvoiceLineRequest;
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
import com.asalkartechworks.himmat.expense.dto.CreateExpenseRequest;
import com.asalkartechworks.himmat.expense.dto.ExpenseComponentDto;
import com.asalkartechworks.himmat.expense.model.ExpenseCategory;
import com.asalkartechworks.himmat.identity.model.AppUser;
import com.asalkartechworks.himmat.identity.model.UserRole;
import com.asalkartechworks.himmat.identity.repository.AppUserRepository;
import com.asalkartechworks.himmat.party.model.Customer;
import com.asalkartechworks.himmat.party.model.CustomerType;
import com.asalkartechworks.himmat.party.repository.CustomerRepository;
import com.asalkartechworks.himmat.purchase.dto.PaymentComponentDto;
import com.asalkartechworks.himmat.purchase.model.PaymentMethod;
import com.asalkartechworks.himmat.security.JwtService;
import com.asalkartechworks.himmat.security.UserPrincipal;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardAndReportsIntegrationTest {

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
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String ownerToken;
    private String managerToken;

    private Customer testCustomer;
    private ProductVariant variantKnownCost;
    private ProductVariant variantUnknownCost;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE operational_expense_component, operational_expense, customer_refund_source, customer_refund_component, customer_refund, customer_return_line, customer_return, sales_invoice_revision_line, sales_invoice_revision, customer_ledger_entry, receipt_allocation, customer_receipt_component, customer_receipt, sales_invoice_line, sales_invoice, customer_site, customer, supplier_ledger_entry, payment_allocation, payment_component, supplier_refund, purchase_return_line, purchase_return, supplier_payment, purchase_line, purchase, stock_movement, inventory_balance, product_variant, product, subcategory, category, brand, supplier, business_event, app_user CASCADE");

        Instant now = Instant.now();

        // 1. Create Users
        AppUser owner = appUserRepository.save(AppUser.builder()
                .username("owner_" + UUID.randomUUID().toString().substring(0, 8))
                .displayName("Shop Owner")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(UserRole.OWNER)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());
        ownerToken = "Bearer " + jwtService.generateToken(UserPrincipal.create(owner));

        AppUser manager = appUserRepository.save(AppUser.builder()
                .username("mgr_" + UUID.randomUUID().toString().substring(0, 8))
                .displayName("Shop Manager")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(UserRole.MANAGER)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());
        managerToken = "Bearer " + jwtService.generateToken(UserPrincipal.create(manager));

        // 2. Customer
        testCustomer = customerRepository.save(Customer.builder()
                .customerCode("CUST-RPT01")
                .name("Suresh Enterprises")
                .normalizedName("suresh enterprises")
                .phone("9988776655")
                .customerType(CustomerType.RETAIL)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // 3. Catalog
        Brand brand = brandRepository.save(Brand.builder()
                .name("Dulux")
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        Category category = categoryRepository.save(Category.builder()
                .name("Wall Paints")
                .salesDiscountPct(BigDecimal.ZERO)
                .purchaseDiscountPct(BigDecimal.ZERO)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        Subcategory subcategory = subcategoryRepository.save(Subcategory.builder()
                .category(category)
                .name("Velvet Touch")
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        Product product = productRepository.save(Product.builder()
                .brand(brand)
                .subcategory(subcategory)
                .name("Dulux Velvet")
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // Variant A: Known Cost = 100.00, Selling = 150.00
        variantKnownCost = productVariantRepository.save(ProductVariant.builder()
                .product(product)
                .sku("DULUX-VEL-1L")
                .sizeLabel("1 Liter")
                .pricingMethod(PricingMethod.FIXED)
                .fixedPriceInclTax(new BigDecimal("150.00"))
                .purchaseCostExclTax(new BigDecimal("100.00"))
                .lowStockThreshold(5)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // Variant B: Unknown Cost = null, Selling = 80.00
        variantUnknownCost = productVariantRepository.save(ProductVariant.builder()
                .product(product)
                .sku("DULUX-VEL-500ML")
                .sizeLabel("500 ML")
                .pricingMethod(PricingMethod.FIXED)
                .fixedPriceInclTax(new BigDecimal("80.00"))
                .purchaseCostExclTax(null)
                .lowStockThreshold(2)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        inventoryBalanceRepository.save(InventoryBalance.builder()
                .variantId(variantKnownCost.getId())
                .variant(variantKnownCost)
                .quantityOnHand(50)
                .openingConfirmed(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        inventoryBalanceRepository.save(InventoryBalance.builder()
                .variantId(variantUnknownCost.getId())
                .variant(variantUnknownCost)
                .quantityOnHand(20)
                .openingConfirmed(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // 4. Create Sales Invoice:
        // Line 1: 10 units @ 150.00 = 1500.00 (known cost 100.00 * 10 = 1000.00, profit = 500.00)
        // Line 2: 5 units @ 80.00 = 400.00 (unknown cost, excluded from known profit)
        CreateInvoiceRequest invoiceRequest = CreateInvoiceRequest.builder()
                .customerId(testCustomer.getId())
                .invoiceDate(LocalDate.now())
                .lines(List.of(
                        InvoiceLineRequest.builder().variantId(variantKnownCost.getId()).quantity(10).build(),
                        InvoiceLineRequest.builder().variantId(variantUnknownCost.getId()).quantity(5).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/invoices")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invoiceRequest)))
                .andExpect(status().isCreated());

        // 5. Create Customer Receipt: 1000.00
        CreateCustomerReceiptRequest receiptRequest = CreateCustomerReceiptRequest.builder()
                .receiptDate(LocalDate.now())
                .totalAmount(new BigDecimal("1000.00"))
                .components(List.of(
                        PaymentComponentDto.builder().method(PaymentMethod.CASH).amount(new BigDecimal("1000.00")).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/customers/" + testCustomer.getId() + "/receipts")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(receiptRequest)))
                .andExpect(status().isCreated());

        // 6. Create Operational Expense: 200.00
        CreateExpenseRequest expenseRequest = CreateExpenseRequest.builder()
                .expenseDate(LocalDate.now())
                .category(ExpenseCategory.SHOP_OPERATIONS)
                .description("Cleaning supplies")
                .amount(new BigDecimal("200.00"))
                .components(List.of(
                        ExpenseComponentDto.builder().method(PaymentMethod.CASH).amount(new BigDecimal("200.00")).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/expenses")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(expenseRequest)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Dashboard summary should return full metrics including profit for Owner")
    void getDashboardSummary_asOwner_success() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grossSales", is("1900.00")))
                .andExpect(jsonPath("$.data.netSales", is("1900.00")))
                .andExpect(jsonPath("$.data.invoiceCount", is(1)))
                .andExpect(jsonPath("$.data.customerReceipts", is("1000.00")))
                .andExpect(jsonPath("$.data.netCollections", is("1000.00")))
                .andExpect(jsonPath("$.data.periodOperatingExpenses", is("200.00")))
                .andExpect(jsonPath("$.data.totalVariantsCount", is(2)))
                .andExpect(jsonPath("$.data.profitSummary", notNullValue()))
                .andExpect(jsonPath("$.data.profitSummary.netRevenue", is("1500.00")))
                .andExpect(jsonPath("$.data.profitSummary.cogs", is("1000.00")))
                .andExpect(jsonPath("$.data.profitSummary.grossProfit", is("500.00")))
                .andExpect(jsonPath("$.data.profitSummary.operatingExpenses", is("200.00")))
                .andExpect(jsonPath("$.data.profitSummary.netProfit", is("300.00")))
                .andExpect(jsonPath("$.data.profitSummary.unknownCostSales", is("400.00")));
    }

    @Test
    @DisplayName("Dashboard summary should hide profit summary for Manager")
    void getDashboardSummary_asManager_hidesProfit() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grossSales", is("1900.00")))
                .andExpect(jsonPath("$.data.profitSummary").doesNotExist());
    }

    @Test
    @DisplayName("Profit report endpoint must return 403 Forbidden for Manager")
    void getProfitReport_asManager_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/profit")
                        .header(HttpHeaders.AUTHORIZATION, managerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/reports/profit/products")
                        .header(HttpHeaders.AUTHORIZATION, managerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Profit report endpoint should return accurate snapshot-based calculations for Owner")
    void getProfitReport_asOwner_success() throws Exception {
        mockMvc.perform(get("/api/v1/reports/profit")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.knownNetRevenue", is("1500.00")))
                .andExpect(jsonPath("$.data.knownCogs", is("1000.00")))
                .andExpect(jsonPath("$.data.knownGrossProfit", is("500.00")))
                .andExpect(jsonPath("$.data.knownGrossMarginPct", is("33.33")))
                .andExpect(jsonPath("$.data.salesValueExcludedUnknownCost", is("400.00")))
                .andExpect(jsonPath("$.data.totalNetSales", is("1900.00")))
                .andExpect(jsonPath("$.data.totalOperatingExpenses", is("200.00")))
                .andExpect(jsonPath("$.data.estimatedNetProfit", is("300.00")))
                .andExpect(jsonPath("$.data.productBreakdown", hasSize(2)));
    }

    @Test
    @DisplayName("Sales report should support both ACTIVITY and DOCUMENT bases")
    void getSalesReport_bases() throws Exception {
        // ACTIVITY basis
        mockMvc.perform(get("/api/v1/reports/sales?basis=ACTIVITY")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalGrossSales", is("1900.00")))
                .andExpect(jsonPath("$.data.totalNetSales", is("1900.00")))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].type", is("INVOICE")));

        // DOCUMENT basis
        mockMvc.perform(get("/api/v1/reports/sales?basis=DOCUMENT")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalGrossSales", is("1900.00")))
                .andExpect(jsonPath("$.data.totalNetSales", is("1900.00")))
                .andExpect(jsonPath("$.data.items", hasSize(1)));
    }

    @Test
    @DisplayName("Cash movement report should accurately record receipts IN and expenses OUT")
    void getCashMovementReport_success() throws Exception {
        mockMvc.perform(get("/api/v1/reports/cash-movements")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCashIn", is("1000.00")))
                .andExpect(jsonPath("$.data.totalCashOut", is("200.00")))
                .andExpect(jsonPath("$.data.netCashMovement", is("800.00")))
                .andExpect(jsonPath("$.data.grandTotalIn", is("1000.00")))
                .andExpect(jsonPath("$.data.grandTotalOut", is("200.00")))
                .andExpect(jsonPath("$.data.netCashflow", is("800.00")))
                .andExpect(jsonPath("$.data.items", hasSize(2)));
    }

    @Test
    @DisplayName("Inventory report should show stock quantities and valuations")
    void getInventoryReport_success() throws Exception {
        mockMvc.perform(get("/api/v1/reports/inventory")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalVariants", is(2)))
                .andExpect(jsonPath("$.data.totalStockQuantity", greaterThanOrEqualTo(50)))
                .andExpect(jsonPath("$.data.items", hasSize(2)));
    }
}
