package com.asalkartechworks.himmat.purchase.model;

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
@Table(name = "purchase_line")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false)
    private Purchase purchase;

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

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Builder.Default
    @Column(name = "returned_quantity", nullable = false)
    private Integer returnedQuantity = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_basis", nullable = false, length = 30)
    private PriceBasis priceBasis;

    @Column(name = "entered_unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal enteredUnitPrice;

    @Builder.Default
    @Column(name = "discount_pct", nullable = false, precision = 7, scale = 4)
    private BigDecimal discountPct = BigDecimal.ZERO;

    @Column(name = "gst_rate_pct", precision = 7, scale = 4)
    private BigDecimal gstRatePct;

    @Column(name = "net_unit_cost", precision = 18, scale = 4)
    private BigDecimal netUnitCost;

    @Column(name = "gross_unit_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal grossUnitCost;

    @Column(name = "line_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal lineTotal;

    @Builder.Default
    @Column(name = "update_product_cost", nullable = false)
    private boolean updateProductCost = false;

    public int getRemainingQuantity() {
        return quantity - (returnedQuantity != null ? returnedQuantity : 0);
    }
}
