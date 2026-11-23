package com.asalkartechworks.himmat.expense.service;

import com.asalkartechworks.himmat.common.dto.PageResponse;
import com.asalkartechworks.himmat.common.errors.AppException;
import com.asalkartechworks.himmat.common.errors.ResourceNotFoundException;
import com.asalkartechworks.himmat.common.money.MoneyPolicy;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.expense.dto.CreateExpenseRequest;
import com.asalkartechworks.himmat.expense.dto.ExpenseComponentDto;
import com.asalkartechworks.himmat.expense.dto.ExpenseResponseDto;
import com.asalkartechworks.himmat.expense.dto.ReverseExpenseRequest;
import com.asalkartechworks.himmat.expense.model.ExpenseCategory;
import com.asalkartechworks.himmat.expense.model.ExpenseStatus;
import com.asalkartechworks.himmat.expense.model.OperationalExpense;
import com.asalkartechworks.himmat.expense.model.OperationalExpenseComponent;
import com.asalkartechworks.himmat.expense.repository.OperationalExpenseComponentRepository;
import com.asalkartechworks.himmat.expense.repository.OperationalExpenseRepository;
import com.asalkartechworks.himmat.purchase.model.PaymentMethod;
import com.asalkartechworks.himmat.shared.idempotency.model.BusinessEvent;
import com.asalkartechworks.himmat.shared.transaction.BusinessTransactionExecutor;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationalExpenseService {

    private final OperationalExpenseRepository expenseRepository;
    private final OperationalExpenseComponentRepository componentRepository;
    private final BusinessTransactionExecutor businessTransactionExecutor;
    private final BusinessClock businessClock;

    public ExpenseResponseDto createExpense(String idempotencyKey, Long actorId, CreateExpenseRequest request) {
        return businessTransactionExecutor.executeTransaction(
                idempotencyKey,
                actorId,
                "OPERATIONAL_EXPENSE_CREATE",
                "Create operational expense: " + request.getDescription(),
                "POST",
                "/api/v1/expenses",
                request,
                ExpenseResponseDto.class,
                event -> doCreateExpense(event, actorId, request)
        );
    }

    private ExpenseResponseDto doCreateExpense(BusinessEvent event, Long actorId, CreateExpenseRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException("VALIDATION_FAILED", "Expense amount must be greater than zero", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        if (request.getComponents() == null || request.getComponents().isEmpty()) {
            throw new AppException("VALIDATION_FAILED", "At least one payment component is required", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        BigDecimal componentSum = BigDecimal.ZERO;
        for (ExpenseComponentDto comp : request.getComponents()) {
            if (comp.getAmount() == null || comp.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new AppException("VALIDATION_FAILED", "Payment component amount must be greater than zero", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (comp.getMethod() == null) {
                throw new AppException("VALIDATION_FAILED", "Payment component method is required", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            componentSum = componentSum.add(comp.getAmount());
        }

        if (componentSum.compareTo(request.getAmount()) != 0) {
            throw new AppException("VALIDATION_FAILED",
                    String.format("Component sum %s does not equal expense amount %s", componentSum, request.getAmount()),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        LocalDate expenseDate = request.getExpenseDate() != null ? request.getExpenseDate() : businessClock.today();
        Long seq = expenseRepository.getNextExpenseSequence();
        String expenseNumber = String.format("EXP-%05d", seq);

        Instant now = Instant.now();
        OperationalExpense expense = OperationalExpense.builder()
                .expenseNumber(expenseNumber)
                .expenseDate(expenseDate)
                .category(request.getCategory())
                .description(request.getDescription().trim())
                .amount(MoneyPolicy.roundMoney(request.getAmount()))
                .status(ExpenseStatus.POSTED)
                .externalReference(request.getExternalReference() != null ? request.getExternalReference().trim() : null)
                .notes(request.getNotes() != null ? request.getNotes().trim() : null)
                .businessEvent(event)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(actorId)
                .updatedBy(actorId)
                .components(new ArrayList<>())
                .build();

        for (ExpenseComponentDto compDto : request.getComponents()) {
            OperationalExpenseComponent comp = OperationalExpenseComponent.builder()
                    .method(compDto.getMethod())
                    .amount(MoneyPolicy.roundMoney(compDto.getAmount()))
                    .onlineReference(compDto.getOnlineReference() != null ? compDto.getOnlineReference().trim() : null)
                    .build();
            expense.addComponent(comp);
        }

        OperationalExpense saved = expenseRepository.save(expense);
        log.info("Created operational expense {} with amount {}", saved.getExpenseNumber(), saved.getAmount());
        return mapToDto(saved);
    }

    public ExpenseResponseDto reverseExpense(String idempotencyKey, Long expenseId, Long actorId, ReverseExpenseRequest request) {
        return businessTransactionExecutor.executeTransaction(
                idempotencyKey,
                actorId,
                "OPERATIONAL_EXPENSE_REVERSAL",
                "Reverse operational expense ID: " + expenseId + ", reason: " + request.getReversalReason(),
                "POST",
                "/api/v1/expenses/" + expenseId + "/reversals",
                request,
                ExpenseResponseDto.class,
                event -> doReverseExpense(expenseId, actorId, request)
        );
    }

    private ExpenseResponseDto doReverseExpense(Long expenseId, Long actorId, ReverseExpenseRequest request) {
        OperationalExpense expense = expenseRepository.findByIdWithLock(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with ID: " + expenseId));

        if (request.getExpectedVersion() != null && !expense.getVersion().equals(request.getExpectedVersion())) {
            throw new AppException("CONFLICT", "Expense was updated by another transaction", HttpStatus.CONFLICT);
        }

        if (expense.getStatus() == ExpenseStatus.REVERSED) {
            throw new AppException("ALREADY_REVERSED", "Expense is already reversed", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Instant now = Instant.now();
        expense.setStatus(ExpenseStatus.REVERSED);
        expense.setReversalReason(request.getReversalReason().trim());
        expense.setReversalBusinessDate(request.getReversalBusinessDate() != null ? request.getReversalBusinessDate() : businessClock.today());
        expense.setReversedAt(now);
        expense.setReversedBy(actorId);
        expense.setUpdatedAt(now);
        expense.setUpdatedBy(actorId);

        OperationalExpense saved = expenseRepository.save(expense);
        log.info("Reversed operational expense {} with reason {}", saved.getExpenseNumber(), saved.getReversalReason());
        return mapToDto(saved);
    }

    @Transactional(readOnly = true)
    public ExpenseResponseDto getExpenseById(Long expenseId) {
        OperationalExpense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with ID: " + expenseId));
        return mapToDto(expense);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponseDto> searchExpenses(
            LocalDate startDate,
            LocalDate endDate,
            ExpenseCategory category,
            ExpenseStatus status,
            PaymentMethod paymentMethod,
            String search,
            Pageable pageable) {

        Specification<OperationalExpense> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("expenseDate"), endDate));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (paymentMethod != null) {
                query.distinct(true);
                predicates.add(cb.equal(root.join("components").get("method"), paymentMethod));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Predicate numMatch = cb.like(cb.lower(root.get("expenseNumber")), pattern);
                Predicate descMatch = cb.like(cb.lower(root.get("description")), pattern);
                Predicate refMatch = cb.like(cb.lower(root.get("externalReference")), pattern);
                predicates.add(cb.or(numMatch, descMatch, refMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<OperationalExpense> page = expenseRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(this::mapToDto));
    }

    public ExpenseResponseDto mapToDto(OperationalExpense expense) {
        List<ExpenseComponentDto> compDtos = expense.getComponents().stream()
                .map(c -> ExpenseComponentDto.builder()
                        .method(c.getMethod())
                        .amount(c.getAmount())
                        .onlineReference(c.getOnlineReference())
                        .build())
                .toList();

        return ExpenseResponseDto.builder()
                .id(expense.getId())
                .expenseNumber(expense.getExpenseNumber())
                .expenseDate(expense.getExpenseDate())
                .category(expense.getCategory())
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .status(expense.getStatus())
                .externalReference(expense.getExternalReference())
                .notes(expense.getNotes())
                .components(compDtos)
                .reversalReason(expense.getReversalReason())
                .reversalBusinessDate(expense.getReversalBusinessDate())
                .reversedAt(expense.getReversedAt())
                .reversedBy(expense.getReversedBy())
                .businessEventId(expense.getBusinessEvent() != null ? expense.getBusinessEvent().getId() : null)
                .version(expense.getVersion())
                .createdAt(expense.getCreatedAt())
                .updatedAt(expense.getUpdatedAt())
                .createdBy(expense.getCreatedBy())
                .updatedBy(expense.getUpdatedBy())
                .build();
    }
}
