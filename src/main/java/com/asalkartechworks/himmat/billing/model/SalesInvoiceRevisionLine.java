package com.asalkartechworks.himmat.billing.model;

import com.asalkartechworks.himmat.catalog.model.ProductVariant;
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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "sales_invoice_revision_line")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesInvoiceRevisionLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revision_id", nullable = false)
    private SalesInvoiceRevision revision;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_line_id", nullable = false)
    private SalesInvoiceLine invoiceLine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(name = "line_index", nullable = false)
    private Integer lineIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", length = 30, nullable = false)
    private RevisionChangeType changeType;

    @Column(name = "previous_quantity")
    private Integer previousQuantity;

    @Column(name = "new_quantity")
    private Integer newQuantity;

    @Builder.Default
    @Column(name = "delta_quantity", nullable = false)
    private Integer deltaQuantity = 0;

    @Column(name = "previous_unit_net_price", precision = 18, scale = 4)
    private BigDecimal previousUnitNetPrice;

    @Column(name = "new_unit_net_price", precision = 18, scale = 4)
    private BigDecimal newUnitNetPrice;

    @Column(name = "previous_line_total", precision = 18, scale = 2)
    private BigDecimal previousLineTotal;

    @Column(name = "new_line_total", precision = 18, scale = 2)
    private BigDecimal newLineTotal;

    @Builder.Default
    @Column(name = "delta_line_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal deltaLineTotal = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "is_price_overridden", nullable = false)
    private boolean isPriceOverridden = false;

    @Column(name = "override_reason", columnDefinition = "TEXT")
    private String overrideReason;
}
