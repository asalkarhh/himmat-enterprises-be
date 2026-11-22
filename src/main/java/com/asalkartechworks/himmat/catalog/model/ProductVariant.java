package com.asalkartechworks.himmat.catalog.model;

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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "product_variant")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "sku", nullable = false, unique = true, length = 100)
    private String sku;

    @Column(name = "size_label", nullable = false, length = 120)
    private String sizeLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_method", nullable = false, length = 30)
    private PricingMethod pricingMethod;

    @Column(name = "mrp_incl_tax", precision = 18, scale = 2)
    private BigDecimal mrpInclTax;

    @Column(name = "fixed_price_incl_tax", precision = 18, scale = 2)
    private BigDecimal fixedPriceInclTax;

    @Column(name = "purchase_cost_excl_tax", precision = 18, scale = 4)
    private BigDecimal purchaseCostExclTax;

    @Column(name = "profit_pct", precision = 7, scale = 4)
    private BigDecimal profitPct;

    @Column(name = "gst_rate_pct", precision = 7, scale = 4)
    private BigDecimal gstRatePct;

    @Column(name = "hsn_code", length = 20)
    private String hsnCode;

    @Builder.Default
    @Column(name = "low_stock_threshold", nullable = false)
    private Integer lowStockThreshold = 0;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

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
}
