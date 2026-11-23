package com.asalkartechworks.himmat.document;

import com.asalkartechworks.himmat.billing.dto.CreateCustomerReceiptRequest;
import com.asalkartechworks.himmat.billing.dto.CreateCustomerRefundRequest;
import com.asalkartechworks.himmat.billing.dto.CreateInvoiceRequest;
import com.asalkartechworks.himmat.billing.dto.CustomerReturnLineRequest;
import com.asalkartechworks.himmat.billing.dto.CustomerReturnRequest;
import com.asalkartechworks.himmat.billing.dto.InvoiceLineRequest;
import com.asalkartechworks.himmat.billing.dto.InvoiceRevisionLineRequest;
import com.asalkartechworks.himmat.billing.dto.InvoiceRevisionRequest;
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
import com.asalkartechworks.himmat.party.model.Supplier;
import com.asalkartechworks.himmat.party.repository.CustomerRepository;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PdfGenerationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ShopSettingsRepository shopSettingsRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private SupplierRepository supplierRepository;

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
    private Customer customer;
    private Supplier supplier;
    private ProductVariant variant;

    private Long invoiceId;
    private Long revisionId;
    private Long customerReceiptId;
    private Long customerReturnId;
    private Long customerRefundId;

    private Long purchaseId;
    private Long purchaseReturnId;
    private Long supplierPaymentId;
    private Long supplierRefundId;

    private Long expenseId;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE operational_expense_component, operational_expense, customer_refund_source, customer_refund_component, customer_refund, customer_return_line, customer_return, sales_invoice_revision_line, sales_invoice_revision, customer_ledger_entry, receipt_allocation, customer_receipt_component, customer_receipt, sales_invoice_line, sales_invoice, customer_site, customer, supplier_ledger_entry, payment_allocation, payment_component, supplier_refund, purchase_return_line, purchase_return, supplier_payment, purchase_line, purchase, stock_movement, inventory_balance, product_variant, product, subcategory, category, brand, supplier, business_event, app_user CASCADE");

        Instant now = Instant.now();

        // 1. User
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

        // 2. Shop Settings
        shopSettingsRepository.save(ShopSettings.builder()
                .id(ShopSettings.SINGLETON_ID)
                .name("Himmat Enterprises")
                .address("Shop #4, Market Yard, Pune, Maharashtra 411037")
                .phone("+91 98220 12345")
                .email("himmat.paints@example.com")
                .gstin("27AABCH1234D1Z5")
                .stateCode("27")
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // 3. Customer & Supplier
        customer = customerRepository.save(Customer.builder()
                .customerCode("CUST-PDF01")
                .name("Kishore Sharma")
                .normalizedName("kishore sharma")
                .phone("9823456789")
                .customerType(CustomerType.CONTRACTOR)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        supplier = supplierRepository.save(Supplier.builder()
                .supplierCode("SUP-PDF01")
                .name("Asian Paints Depot")
                .normalizedName("asian paints depot")
                .phone("020-24455667")
                .contactPerson("Mahesh Joshi")
                .gstin("27AAACA1234A1Z1")
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // 4. Catalog
        Brand brand = brandRepository.save(Brand.builder().name("Asian Paints").isActive(true).version(0L).createdAt(now).updatedAt(now).build());
        Category category = categoryRepository.save(Category.builder().name("Enamel").salesDiscountPct(BigDecimal.ZERO).purchaseDiscountPct(BigDecimal.ZERO).isActive(true).version(0L).createdAt(now).updatedAt(now).build());
        Subcategory subcategory = subcategoryRepository.save(Subcategory.builder().category(category).name("Gloss").isActive(true).version(0L).createdAt(now).updatedAt(now).build());
        Product product = productRepository.save(Product.builder().brand(brand).subcategory(subcategory).name("Apcolite Enamel").isActive(true).version(0L).createdAt(now).updatedAt(now).build());

        variant = productVariantRepository.save(ProductVariant.builder()
                .product(product)
                .sku("APC-GLOSS-1L")
                .sizeLabel("1 Liter")
                .pricingMethod(PricingMethod.FIXED)
                .fixedPriceInclTax(new BigDecimal("250.00"))
                .purchaseCostExclTax(new BigDecimal("180.00"))
                .gstRatePct(new BigDecimal("18.00"))
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        inventoryBalanceRepository.save(InventoryBalance.builder()
                .variantId(variant.getId())
                .variant(variant)
                .quantityOnHand(100)
                .openingConfirmed(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // 5. Create Purchase
        CreatePurchaseRequest purchaseRequest = CreatePurchaseRequest.builder()
                .supplierId(supplier.getId())
                .purchaseDate(LocalDate.now())
                .supplierInvoiceNumber("DEPOT-INV-9988")
                .lines(List.of(
                        PurchaseLineRequest.builder()
                                .variantId(variant.getId())
                                .quantity(20)
                                .priceBasis(PriceBasis.GST_EXCLUSIVE)
                                .enteredUnitPrice(new BigDecimal("180.00"))
                                .gstRatePct(new BigDecimal("18.00"))
                                .build()
                ))
                .build();

        MvcResult purResult = mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(purchaseRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        purchaseId = objectMapper.readTree(purResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        long pLineId = objectMapper.readTree(purResult.getResponse().getContentAsString()).path("data").path("lines").get(0).path("id").asLong();

        // 6. Create Supplier Payment
        CreateSupplierPaymentRequest paymentReq = CreateSupplierPaymentRequest.builder()
                .paymentDate(LocalDate.now())
                .totalAmount(new BigDecimal("2000.00"))
                .components(List.of(PaymentComponentDto.builder().method(PaymentMethod.ONLINE).amount(new BigDecimal("2000.00")).onlineReference("UTR-SUPP-1").build()))
                .build();
        MvcResult payResult = mockMvc.perform(post("/api/v1/suppliers/" + supplier.getId() + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentReq)))
                .andExpect(status().isCreated())
                .andReturn();
        supplierPaymentId = objectMapper.readTree(payResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        // 7. Create Purchase Return
        CreatePurchaseReturnRequest pretReq = CreatePurchaseReturnRequest.builder()
                .returnDate(LocalDate.now())
                .reason("Damaged tins on delivery")
                .lines(List.of(
                        PurchaseReturnLineRequest.builder()
                                .purchaseLineId(pLineId)
                                .quantity(2)
                                .build()
                ))
                .build();
        MvcResult pretResult = mockMvc.perform(post("/api/v1/purchases/" + purchaseId + "/returns")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pretReq)))
                .andExpect(status().isCreated())
                .andReturn();
        purchaseReturnId = objectMapper.readTree(pretResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        // 8. Create Supplier Refund
        CreateSupplierRefundRequest srefReq = CreateSupplierRefundRequest.builder()
                .refundDate(LocalDate.now())
                .reason("Rebate refund")
                .totalAmount(new BigDecimal("360.00"))
                .components(List.of(PaymentComponentDto.builder().method(PaymentMethod.ONLINE).amount(new BigDecimal("360.00")).onlineReference("REF-DEPOT-1").build()))
                .build();
        MvcResult srefResult = mockMvc.perform(post("/api/v1/suppliers/" + supplier.getId() + "/refunds")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(srefReq)))
                .andExpect(status().isCreated())
                .andReturn();
        supplierRefundId = objectMapper.readTree(srefResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        // 9. Create Sales Invoice
        CreateInvoiceRequest invReq = CreateInvoiceRequest.builder()
                .customerId(customer.getId())
                .invoiceDate(LocalDate.now())
                .lines(List.of(
                        InvoiceLineRequest.builder().variantId(variant.getId()).quantity(10).build()
                ))
                .build();
        MvcResult invResult = mockMvc.perform(post("/api/v1/invoices")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invReq)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode invJson = objectMapper.readTree(invResult.getResponse().getContentAsString()).path("data");
        invoiceId = invJson.path("id").asLong();
        long lineId = invJson.path("lines").get(0).path("id").asLong();

        // 10. Invoice Revision
        InvoiceRevisionRequest revReq = InvoiceRevisionRequest.builder()
                .expectedVersion(0L)
                .reason("Customer added 2 more tins")
                .lines(List.of(
                        InvoiceRevisionLineRequest.builder()
                                .invoiceLineId(lineId)
                                .variantId(variant.getId())
                                .quantity(12)
                                .build()
                ))
                .build();
        MvcResult revResult = mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/revisions")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revReq)))
                .andExpect(status().isCreated())
                .andReturn();
        revisionId = objectMapper.readTree(revResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        // 11. Customer Receipt
        CreateCustomerReceiptRequest recReq = CreateCustomerReceiptRequest.builder()
                .receiptDate(LocalDate.now())
                .totalAmount(new BigDecimal("1500.00"))
                .components(List.of(PaymentComponentDto.builder().method(PaymentMethod.CASH).amount(new BigDecimal("1500.00")).build()))
                .build();
        MvcResult recResult = mockMvc.perform(post("/api/v1/customers/" + customer.getId() + "/receipts")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(recReq)))
                .andExpect(status().isCreated())
                .andReturn();
        customerReceiptId = objectMapper.readTree(recResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        // 12. Customer Return
        CustomerReturnRequest retReq = CustomerReturnRequest.builder()
                .returnDate(LocalDate.now())
                .reason("Wrong shade requested by painter")
                .lines(List.of(
                        CustomerReturnLineRequest.builder().invoiceLineId(lineId).quantity(1).build()
                ))
                .build();
        MvcResult retResult = mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/returns")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(retReq)))
                .andExpect(status().isCreated())
                .andReturn();
        customerReturnId = objectMapper.readTree(retResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        // 13. Customer Refund
        CreateCustomerRefundRequest refReq = CreateCustomerRefundRequest.builder()
                .refundDate(LocalDate.now())
                .reason("Advance refund on return")
                .totalAmount(new BigDecimal("200.00"))
                .components(List.of(PaymentComponentDto.builder().method(PaymentMethod.CASH).amount(new BigDecimal("200.00")).build()))
                .build();
        MvcResult refResult = mockMvc.perform(post("/api/v1/customers/" + customer.getId() + "/refunds")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refReq)))
                .andExpect(status().isCreated())
                .andReturn();
        customerRefundId = objectMapper.readTree(refResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        // 14. Operational Expense
        CreateExpenseRequest expReq = CreateExpenseRequest.builder()
                .expenseDate(LocalDate.now())
                .category(ExpenseCategory.SHOP_OPERATIONS)
                .description("Hardware store maintenance")
                .amount(new BigDecimal("450.00"))
                .components(List.of(ExpenseComponentDto.builder().method(PaymentMethod.CASH).amount(new BigDecimal("450.00")).build()))
                .build();
        MvcResult expResult = mockMvc.perform(post("/api/v1/expenses")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(expReq)))
                .andExpect(status().isCreated())
                .andReturn();
        expenseId = objectMapper.readTree(expResult.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private void verifyPdfResponse(String url) throws Exception {
        MvcResult result = mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().exists("Content-Disposition"))
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(bytes).isNotNull();
        assertThat(bytes.length).isGreaterThan(200);

        // Verify PDF Magic Bytes "%PDF-1."
        String header = new String(bytes, 0, 7, StandardCharsets.US_ASCII);
        assertThat(header).startsWith("%PDF-1.");
    }

    @Test
    @DisplayName("1. Invoice PDF (Normal format)")
    void testInvoicePdf_normal() throws Exception {
        verifyPdfResponse("/api/v1/invoices/" + invoiceId + "/pdf?format=NORMAL");
    }

    @Test
    @DisplayName("2. Invoice PDF (GST format)")
    void testInvoicePdf_gst() throws Exception {
        verifyPdfResponse("/api/v1/invoices/" + invoiceId + "/pdf?format=GST");
    }

    @Test
    @DisplayName("3. Invoice Revision PDF")
    void testInvoiceRevisionPdf() throws Exception {
        verifyPdfResponse("/api/v1/invoices/" + invoiceId + "/revisions/" + revisionId + "/pdf");
    }

    @Test
    @DisplayName("4. Customer Receipt PDF")
    void testCustomerReceiptPdf() throws Exception {
        verifyPdfResponse("/api/v1/customer-receipts/" + customerReceiptId + "/pdf");
    }

    @Test
    @DisplayName("5. Customer Return Credit Note PDF")
    void testCustomerReturnPdf() throws Exception {
        verifyPdfResponse("/api/v1/customer-returns/" + customerReturnId + "/pdf");
    }

    @Test
    @DisplayName("6. Customer Refund Voucher PDF")
    void testCustomerRefundPdf() throws Exception {
        verifyPdfResponse("/api/v1/customer-refunds/" + customerRefundId + "/pdf");
    }

    @Test
    @DisplayName("7. Inward Purchase PDF")
    void testPurchasePdf() throws Exception {
        verifyPdfResponse("/api/v1/purchases/" + purchaseId + "/pdf");
    }

    @Test
    @DisplayName("8. Purchase Return Debit Note PDF")
    void testPurchaseReturnPdf() throws Exception {
        verifyPdfResponse("/api/v1/purchases/returns/" + purchaseReturnId + "/pdf");
    }

    @Test
    @DisplayName("9. Supplier Payment Voucher PDF")
    void testSupplierPaymentPdf() throws Exception {
        verifyPdfResponse("/api/v1/supplier-payments/" + supplierPaymentId + "/pdf");
    }

    @Test
    @DisplayName("10. Supplier Refund Receipt PDF")
    void testSupplierRefundPdf() throws Exception {
        verifyPdfResponse("/api/v1/supplier-refunds/" + supplierRefundId + "/pdf");
    }

    @Test
    @DisplayName("11. Customer Statement of Account PDF")
    void testCustomerLedgerPdf() throws Exception {
        verifyPdfResponse("/api/v1/customers/" + customer.getId() + "/ledger/pdf");
    }

    @Test
    @DisplayName("12. Supplier Statement of Account PDF")
    void testSupplierLedgerPdf() throws Exception {
        verifyPdfResponse("/api/v1/suppliers/" + supplier.getId() + "/ledger/pdf");
    }

    @Test
    @DisplayName("13. Operational Expense Voucher PDF")
    void testExpensePdf() throws Exception {
        verifyPdfResponse("/api/v1/expenses/" + expenseId + "/pdf");
    }
}
