package com.asalkartechworks.himmat.billing.dto;

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
public class CustomerReturnResponseDto {

    private Long id;
    private String returnNumber;
    private Long invoiceId;
    private String invoiceNumber;
    private Long customerId;
    private String customerName;
    private LocalDate returnDate;
    private BigDecimal totalReturnAmount;
    private BigDecimal releasedAllocationAmount;
    private String reason;
    private List<CustomerReturnLineDto> lines;
    private Long businessEventId;
    private Long version;
    private Instant createdAt;
}
