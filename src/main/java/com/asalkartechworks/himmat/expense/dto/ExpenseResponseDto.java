package com.asalkartechworks.himmat.expense.dto;

import com.asalkartechworks.himmat.expense.model.ExpenseCategory;
import com.asalkartechworks.himmat.expense.model.ExpenseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseResponseDto {

    private Long id;
    private String expenseNumber;
    private LocalDate expenseDate;
    private ExpenseCategory category;
    private String description;
    private BigDecimal amount;
    private ExpenseStatus status;
    private String externalReference;
    private String notes;
    private List<ExpenseComponentDto> components;
    private String reversalReason;
    private LocalDate reversalBusinessDate;
    private Instant reversedAt;
    private Long reversedBy;
    private Long businessEventId;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
