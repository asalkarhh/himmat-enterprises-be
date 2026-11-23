package com.asalkartechworks.himmat.expense;

import com.asalkartechworks.himmat.expense.dto.CreateExpenseRequest;
import com.asalkartechworks.himmat.expense.dto.ExpenseComponentDto;
import com.asalkartechworks.himmat.expense.dto.ReverseExpenseRequest;
import com.asalkartechworks.himmat.expense.model.ExpenseCategory;
import com.asalkartechworks.himmat.expense.model.ExpenseStatus;
import com.asalkartechworks.himmat.expense.model.OperationalExpense;
import com.asalkartechworks.himmat.expense.repository.OperationalExpenseRepository;
import com.asalkartechworks.himmat.identity.model.AppUser;
import com.asalkartechworks.himmat.identity.model.UserRole;
import com.asalkartechworks.himmat.identity.repository.AppUserRepository;
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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExpenseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private OperationalExpenseRepository expenseRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String ownerToken;
    private AppUser ownerUser;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE operational_expense_component, operational_expense, business_event, app_user CASCADE");

        Instant now = Instant.now();
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
    }

    @Test
    @DisplayName("Should create operational expense with CASH and ONLINE split successfully")
    void createExpense_success() throws Exception {
        CreateExpenseRequest request = CreateExpenseRequest.builder()
                .expenseDate(LocalDate.now())
                .category(ExpenseCategory.SHOP_OPERATIONS)
                .description("Shop lighting repairs and supplies")
                .amount(new BigDecimal("1500.00"))
                .components(List.of(
                        ExpenseComponentDto.builder()
                                .method(PaymentMethod.CASH)
                                .amount(new BigDecimal("500.00"))
                                .build(),
                        ExpenseComponentDto.builder()
                                .method(PaymentMethod.ONLINE)
                                .amount(new BigDecimal("1000.00"))
                                .onlineReference("UPI-123456789")
                                .build()
                ))
                .externalReference("INV-REPAIR-01")
                .notes("Emergency electric wiring repair")
                .build();

        mockMvc.perform(post("/api/v1/expenses")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.expenseNumber").exists())
                .andExpect(jsonPath("$.data.category", is("SHOP_OPERATIONS")))
                .andExpect(jsonPath("$.data.description", is("Shop lighting repairs and supplies")))
                .andExpect(jsonPath("$.data.amount", is("1500.00")))
                .andExpect(jsonPath("$.data.status", is("POSTED")))
                .andExpect(jsonPath("$.data.components", hasSize(2)));
    }

    @Test
    @DisplayName("Should fail when component sum does not match total amount")
    void createExpense_componentSumMismatch_fails() throws Exception {
        CreateExpenseRequest request = CreateExpenseRequest.builder()
                .expenseDate(LocalDate.now())
                .category(ExpenseCategory.RENT)
                .description("Monthly shop rent")
                .amount(new BigDecimal("20000.00"))
                .components(List.of(
                        ExpenseComponentDto.builder()
                                .method(PaymentMethod.ONLINE)
                                .amount(new BigDecimal("18000.00"))
                                .onlineReference("NEFT-998877")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/expenses")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")));
    }

    @Test
    @DisplayName("Should return idempotent cached response on duplicate request")
    void createExpense_idempotency_returnsCachedResponse() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        CreateExpenseRequest request = CreateExpenseRequest.builder()
                .expenseDate(LocalDate.now())
                .category(ExpenseCategory.TRANSPORT)
                .description("Goods tempo transport charges")
                .amount(new BigDecimal("600.00"))
                .components(List.of(
                        ExpenseComponentDto.builder()
                                .method(PaymentMethod.CASH)
                                .amount(new BigDecimal("600.00"))
                                .build()
                ))
                .build();

        MvcResult result1 = mockMvc.perform(post("/api/v1/expenses")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult result2 = mockMvc.perform(post("/api/v1/expenses")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json1 = objectMapper.readTree(result1.getResponse().getContentAsString()).path("data");
        JsonNode json2 = objectMapper.readTree(result2.getResponse().getContentAsString()).path("data");

        assertThat(json1.path("id").asLong()).isEqualTo(json2.path("id").asLong());
        assertThat(json1.path("expenseNumber").asText()).isEqualTo(json2.path("expenseNumber").asText());
        assertThat(expenseRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should successfully reverse an operational expense with audit trail")
    void reverseExpense_success() throws Exception {
        CreateExpenseRequest createRequest = CreateExpenseRequest.builder()
                .expenseDate(LocalDate.now())
                .category(ExpenseCategory.UTILITIES)
                .description("Electricity bill payment")
                .amount(new BigDecimal("3500.00"))
                .components(List.of(
                        ExpenseComponentDto.builder()
                                .method(PaymentMethod.ONLINE)
                                .amount(new BigDecimal("3500.00"))
                                .onlineReference("MSEB-PAY-1122")
                                .build()
                ))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/expenses")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createdJson = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data");
        long expenseId = createdJson.path("id").asLong();
        long version = createdJson.path("version").asLong();

        ReverseExpenseRequest reverseRequest = ReverseExpenseRequest.builder()
                .expectedVersion(version)
                .reversalReason("Wrong bill number paid, reimbursed by landlord")
                .reversalBusinessDate(LocalDate.now())
                .build();

        mockMvc.perform(post("/api/v1/expenses/" + expenseId + "/reversals")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reverseRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("REVERSED")))
                .andExpect(jsonPath("$.data.reversalReason", is("Wrong bill number paid, reimbursed by landlord")))
                .andExpect(jsonPath("$.data.reversedBy").exists())
                .andExpect(jsonPath("$.data.reversedAt").exists());

        OperationalExpense expense = expenseRepository.findById(expenseId).orElseThrow();
        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.REVERSED);
    }

    @Test
    @DisplayName("Should fail when reversing an already reversed expense")
    void reverseExpense_alreadyReversed_fails() throws Exception {
        CreateExpenseRequest createRequest = CreateExpenseRequest.builder()
                .expenseDate(LocalDate.now())
                .category(ExpenseCategory.OTHER)
                .description("Tea and snacks for staff")
                .amount(new BigDecimal("150.00"))
                .components(List.of(
                        ExpenseComponentDto.builder()
                                .method(PaymentMethod.CASH)
                                .amount(new BigDecimal("150.00"))
                                .build()
                ))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/expenses")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        long expenseId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        ReverseExpenseRequest reverseRequest = ReverseExpenseRequest.builder()
                .reversalReason("Entered twice by mistake")
                .build();

        mockMvc.perform(post("/api/v1/expenses/" + expenseId + "/reversals")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reverseRequest)))
                .andExpect(status().isOk());

        // Attempting second reversal
        mockMvc.perform(post("/api/v1/expenses/" + expenseId + "/reversals")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reverseRequest)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("ALREADY_REVERSED")));
    }

    @Test
    @DisplayName("Should filter expenses by date, category, and payment method")
    void searchExpenses_filters() throws Exception {
        CreateExpenseRequest exp1 = CreateExpenseRequest.builder()
                .expenseDate(LocalDate.now())
                .category(ExpenseCategory.SALARY)
                .description("Worker weekly advance")
                .amount(new BigDecimal("2000.00"))
                .components(List.of(ExpenseComponentDto.builder().method(PaymentMethod.CASH).amount(new BigDecimal("2000.00")).build()))
                .build();

        CreateExpenseRequest exp2 = CreateExpenseRequest.builder()
                .expenseDate(LocalDate.now())
                .category(ExpenseCategory.RENT)
                .description("Warehouse monthly rent")
                .amount(new BigDecimal("10000.00"))
                .components(List.of(ExpenseComponentDto.builder().method(PaymentMethod.ONLINE).amount(new BigDecimal("10000.00")).build()))
                .build();

        mockMvc.perform(post("/api/v1/expenses")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exp1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/expenses")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exp2)))
                .andExpect(status().isCreated());

        // Filter by category SALARY
        mockMvc.perform(get("/api/v1/expenses?category=SALARY")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].category", is("SALARY")));

        // Filter by paymentMethod CASH
        mockMvc.perform(get("/api/v1/expenses?paymentMethod=CASH")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].amount", is("2000.00")));
    }
}
