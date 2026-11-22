package com.asalkartechworks.himmat.billing.model;

import com.asalkartechworks.himmat.shared.idempotency.model.BusinessEvent;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales_invoice_revision")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesInvoiceRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private SalesInvoice invoice;

    @Column(name = "revision_number", nullable = false)
    private Integer revisionNumber;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_event_id", nullable = false)
    private BusinessEvent businessEvent;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "previous_subtotal_excl_tax", precision = 18, scale = 2)
    private BigDecimal previousSubtotalExclTax;

    @Column(name = "previous_total_discount", nullable = false, precision = 18, scale = 2)
    private BigDecimal previousTotalDiscount;

    @Column(name = "previous_total_taxable", precision = 18, scale = 2)
    private BigDecimal previousTotalTaxable;

    @Column(name = "previous_total_gst", precision = 18, scale = 2)
    private BigDecimal previousTotalGst;

    @Column(name = "previous_total_payable", nullable = false, precision = 18, scale = 2)
    private BigDecimal previousTotalPayable;

    @Column(name = "new_subtotal_excl_tax", precision = 18, scale = 2)
    private BigDecimal newSubtotalExclTax;

    @Column(name = "new_total_discount", nullable = false, precision = 18, scale = 2)
    private BigDecimal newTotalDiscount;

    @Column(name = "new_total_taxable", precision = 18, scale = 2)
    private BigDecimal newTotalTaxable;

    @Column(name = "new_total_gst", precision = 18, scale = 2)
    private BigDecimal newTotalGst;

    @Column(name = "new_total_payable", nullable = false, precision = 18, scale = 2)
    private BigDecimal newTotalPayable;

    @Column(name = "delta_payable_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal deltaPayableAmount;

    @Builder.Default
    @Column(name = "released_allocation_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal releasedAllocationAmount = BigDecimal.ZERO;

    @Column(name = "snapshot_payload", nullable = false, columnDefinition = "TEXT")
    private String snapshotPayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "revision", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineIndex ASC")
    private List<SalesInvoiceRevisionLine> lines = new ArrayList<>();

    public void addLine(SalesInvoiceRevisionLine line) {
        lines.add(line);
        line.setRevision(this);
    }
}
