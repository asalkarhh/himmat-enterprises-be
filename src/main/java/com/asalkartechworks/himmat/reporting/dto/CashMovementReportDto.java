package com.asalkartechworks.himmat.reporting.dto;

import com.asalkartechworks.himmat.purchase.model.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashMovementReportDto {

    private LocalDate startDate;
    private LocalDate endDate;

    private BigDecimal totalCashIn;
    private BigDecimal totalCashOut;
    private BigDecimal netCashMovement;

    private BigDecimal totalOnlineIn;
    private BigDecimal totalOnlineOut;
    private BigDecimal netOnlineMovement;

    private BigDecimal grandTotalIn;
    private BigDecimal grandTotalOut;
    private BigDecimal netCashflow;

    private List<CashMovementItemDto> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CashMovementItemDto {
        private LocalDate date;
        private String movementType; // IN, OUT
        private String sourceType; // CUSTOMER_RECEIPT, SUPPLIER_REFUND, SUPPLIER_PAYMENT, CUSTOMER_REFUND, OPERATIONAL_EXPENSE
        private String sourceNumber;
        private String partyName;
        private PaymentMethod method; // CASH, ONLINE
        private BigDecimal amount;
        private String reference;
    }
}
