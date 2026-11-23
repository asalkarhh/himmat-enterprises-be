package com.asalkartechworks.himmat.expense.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReverseExpenseRequest {

    private Long expectedVersion;

    @NotBlank(message = "Reversal reason is required")
    private String reversalReason;

    private LocalDate reversalBusinessDate;
}
