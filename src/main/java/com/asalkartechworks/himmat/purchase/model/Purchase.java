package com.asalkartechworks.himmat.purchase.model;

import com.asalkartechworks.himmat.party.model.Supplier;
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
@Table(name = "purchase")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_number", nullable = false, unique = true, length = 30)
    private String purchaseNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "supplier_invoice_number", length = 100)
    private String supplierInvoiceNumber;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private PurchaseStatus status = PurchaseStatus.POSTED;

    @Column(name = "supplier_name", nullable = false, length = 200)
    private String supplierName;

    @Column(name = "supplier_contact", length = 30)
    private String supplierContact;

    @Column(name = "supplier_gstin", length = 20)
    private String supplierGstin;

    @Column(name = "subtotal_excl_tax", nullable = false, precision = 18, scale = 2)
    private BigDecimal subtotalExclTax;

    @Builder.Default
    @Column(name = "total_discount_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalDiscountAmount = BigDecimal.ZERO;

    @Column(name = "total_gst_amount", precision = 18, scale = 2)
    private BigDecimal totalGstAmount;

    @Column(name = "total_payable_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalPayableAmount;

    @Builder.Default
    @Column(name = "total_allocated_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAllocatedAmount = BigDecimal.ZERO;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_event_id", nullable = false)
    private BusinessEvent businessEvent;

    @Builder.Default
    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineIndex ASC")
    private List<PurchaseLine> lines = new ArrayList<>();

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

    public void addLine(PurchaseLine line) {
        lines.add(line);
        line.setPurchase(this);
    }

    public BigDecimal getTotalReturnedAmount() {
        if (lines == null || lines.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return lines.stream()
                .map(l -> {
                    BigDecimal unitCost = l.getGrossUnitCost() != null ? l.getGrossUnitCost() : BigDecimal.ZERO;
                    int retQty = l.getReturnedQuantity() != null ? l.getReturnedQuantity() : 0;
                    return unitCost.multiply(BigDecimal.valueOf(retQty)).setScale(2, java.math.RoundingMode.HALF_UP);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getEffectivePayableAmount() {
        BigDecimal total = totalPayableAmount != null ? totalPayableAmount : BigDecimal.ZERO;
        return total.subtract(getTotalReturnedAmount()).max(BigDecimal.ZERO);
    }

    public BigDecimal getOutstandingAmount() {
        BigDecimal allocated = totalAllocatedAmount != null ? totalAllocatedAmount : BigDecimal.ZERO;
        return getEffectivePayableAmount().subtract(allocated).max(BigDecimal.ZERO);
    }
}
