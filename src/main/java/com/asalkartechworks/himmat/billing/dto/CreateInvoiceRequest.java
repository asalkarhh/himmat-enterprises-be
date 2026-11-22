package com.asalkartechworks.himmat.billing.dto;

import com.asalkartechworks.himmat.billing.model.InvoiceDisplayFormat;
import com.asalkartechworks.himmat.purchase.dto.PaymentComponentDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class CreateInvoiceRequest {

    @NotNull(message = "customerId is required")
    private Long customerId;

    private Long siteId;

    private LocalDate invoiceDate;

    @Builder.Default
    private InvoiceDisplayFormat displayFormat = InvoiceDisplayFormat.NORMAL;

    private String notes;

    @NotEmpty(message = "Invoice must contain at least one line")
    @Valid
    private List<InvoiceLineRequest> lines;

    private String pricingFingerprint;

    @Valid
    private List<PaymentComponentDto> receiptComponents;

    @DecimalMin(value = "0.01", message = "applyAdvanceAmount must be greater than 0")
    private BigDecimal applyAdvanceAmount;
}
