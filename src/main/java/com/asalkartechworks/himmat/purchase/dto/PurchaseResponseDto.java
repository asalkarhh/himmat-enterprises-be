package com.asalkartechworks.himmat.purchase.dto;

import com.asalkartechworks.himmat.purchase.model.PurchaseStatus;
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
public class PurchaseResponseDto {
    private Long id;
    private String purchaseNumber;
    private Long supplierId;
    private String supplierName;
    private String supplierContact;
    private String supplierGstin;
    private String supplierInvoiceNumber;
    private LocalDate purchaseDate;
    private PurchaseStatus status;
    private BigDecimal subtotalExclTax;
    private BigDecimal totalDiscountAmount;
    private BigDecimal totalGstAmount;
    private BigDecimal totalPayableAmount;
    private BigDecimal totalAllocatedAmount;
    private BigDecimal outstandingBalance;
    private String notes;
    private List<PurchaseLineResponseDto> lines;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
