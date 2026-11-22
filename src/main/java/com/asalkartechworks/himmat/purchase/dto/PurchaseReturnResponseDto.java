package com.asalkartechworks.himmat.purchase.dto;

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
public class PurchaseReturnResponseDto {
    private Long id;
    private String returnNumber;
    private Long purchaseId;
    private String purchaseNumber;
    private Long supplierId;
    private String supplierName;
    private LocalDate returnDate;
    private BigDecimal totalReturnAmount;
    private String reason;
    private List<PurchaseReturnLineResponseDto> lines;
    private Long version;
    private Instant createdAt;
}
