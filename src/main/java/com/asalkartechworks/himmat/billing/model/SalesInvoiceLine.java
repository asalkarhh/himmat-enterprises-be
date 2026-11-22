package com.asalkartechworks.himmat.billing.model;

import com.asalkartechworks.himmat.catalog.model.PricingMethod;
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
@Table(name = "sales_invoice_line")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesInvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private SalesInvoice invoice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(name = "line_index", nullable = false)
    private Integer lineIndex;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "sku", nullable = false, length = 100)
    private String sku;

    @Column(name = "size_label", nullable = false, length = 120)
    private String sizeLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_method", nullable = false, length = 30)
    private PricingMethod pricingMethod;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "base_unit_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal baseUnitPrice;

    @Builder.Default
    @Column(name = "discount_pct", nullable = false, precision = 7, scale = 4)
    private BigDecimal discountPct = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "discount_source", nullable = false, length = 50)
    private String discountSource = "NONE";

    @Column(name = "unit_net_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitNetPrice;

    @Column(name = "line_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal lineTotal;

    @Builder.Default
    @Column(name = "returned_quantity", nullable = false)
    private Integer returnedQuantity = 0;

    @Builder.Default
    @Column(name = "is_active_in_current_revision", nullable = false)
    private boolean isActiveInCurrentRevision = true;

    @Builder.Default
    @Column(name = "is_price_overridden", nullable = false)
    private boolean isPriceOverridden = false;

    @Column(name = "override_reason", columnDefinition = "TEXT")
    private String overrideReason;

    @Column(name = "gst_rate_pct", precision = 7, scale = 4)
    private BigDecimal gstRatePct;

    @Column(name = "taxable_amount", precision = 18, scale = 2)
    private BigDecimal taxableAmount;

    @Column(name = "tax_amount", precision = 18, scale = 2)
    private BigDecimal taxAmount;

    @Builder.Default
    @Column(name = "tax_breakdown_available", nullable = false)
    private boolean taxBreakdownAvailable = false;

    @Column(name = "purchase_cost_excl_tax", precision = 18, scale = 4)
    private BigDecimal purchaseCostExclTax;

    @Builder.Default
    @Column(name = "cost_available", nullable = false)
    private boolean costAvailable = false;

    public int getRemainingReturnableQuantity() {
        int q = quantity != null ? quantity : 0;
        int r = returnedQuantity != null ? returnedQuantity : 0;
        return Math.max(0, q - r);
    }
}
