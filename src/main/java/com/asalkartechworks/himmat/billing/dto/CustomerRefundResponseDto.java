package com.asalkartechworks.himmat.billing.dto;

import com.asalkartechworks.himmat.purchase.dto.PaymentComponentDto;
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
public class CustomerRefundResponseDto {

    private Long id;
    private String refundNumber;
    private Long customerId;
    private String customerName;
    private LocalDate refundDate;
    private BigDecimal totalAmount;
    private String reason;
    private List<PaymentComponentDto> components;
    private List<CustomerRefundSourceDto> sources;
    private Long businessEventId;
    private Long version;
    private Instant createdAt;
}
