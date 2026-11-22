package com.asalkartechworks.himmat.billing.model;

import com.asalkartechworks.himmat.party.model.Customer;
import com.asalkartechworks.himmat.party.model.CustomerSite;
import com.asalkartechworks.himmat.shared.idempotency.model.BusinessEvent;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales_invoice")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 30)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_site_id")
    private CustomerSite customerSite;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "display_format", nullable = false, length = 20)
    private InvoiceDisplayFormat displayFormat = InvoiceDisplayFormat.NORMAL;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private InvoiceStatus status = InvoiceStatus.POSTED;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "settlement_status", nullable = false, length = 30)
    private SettlementStatus settlementStatus = SettlementStatus.UNPAID;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @Column(name = "customer_phone", length = 30)
    private String customerPhone;

    @Column(name = "customer_gstin", length = 20)
    private String customerGstin;

    @Column(name = "customer_address", columnDefinition = "TEXT")
    private String customerAddress;

    @Column(name = "site_name", length = 200)
    private String siteName;

    @Column(name = "site_address", columnDefinition = "TEXT")
    private String siteAddress;

    @Column(name = "subtotal_excl_tax", precision = 18, scale = 2)
    private BigDecimal subtotalExclTax;

    @Builder.Default
    @Column(name = "total_discount_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalDiscountAmount = BigDecimal.ZERO;

    @Column(name = "total_taxable_amount", precision = 18, scale = 2)
    private BigDecimal totalTaxableAmount;

    @Column(name = "total_gst_amount", precision = 18, scale = 2)
    private BigDecimal totalGstAmount;

    @Column(name = "total_payable_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalPayableAmount;

    @Builder.Default
    @Column(name = "total_allocated_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAllocatedAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "tax_breakdown_available", nullable = false)
    private boolean taxBreakdownAvailable = false;

    @Column(name = "pricing_fingerprint", length = 128)
    private String pricingFingerprint;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_event_id", nullable = false)
    private BusinessEvent businessEvent;

    @Builder.Default
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineIndex ASC")
    private List<SalesInvoiceLine> lines = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    public void addLine(SalesInvoiceLine line) {
        lines.add(line);
        line.setInvoice(this);
    }

    public BigDecimal getOutstandingAmount() {
        BigDecimal allocated = totalAllocatedAmount != null ? totalAllocatedAmount : BigDecimal.ZERO;
        return totalPayableAmount.subtract(allocated).max(BigDecimal.ZERO);
    }
}
