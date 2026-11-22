package com.asalkartechworks.himmat.inventory.model;

import com.asalkartechworks.himmat.catalog.model.ProductVariant;
import com.asalkartechworks.himmat.shared.idempotency.model.BusinessEvent;
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

import java.time.Instant;

/**
 * Immutable ledger of all inventory quantity changes.
 * Append-only; updates and deletions are strictly prohibited.
 */
@Entity
@Table(name = "stock_movement")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_event_id", nullable = false)
    private BusinessEvent businessEvent;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", length = 50, nullable = false)
    private StockMovementType movementType;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 10, nullable = false)
    private StockMovementDirection direction;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "quantity_before", nullable = false)
    private Integer quantityBefore;

    @Column(name = "quantity_after", nullable = false)
    private Integer quantityAfter;

    @Column(name = "source_entity_type", length = 50, nullable = false)
    private String sourceEntityType;

    @Column(name = "source_entity_id")
    private Long sourceEntityId;

    @Column(name = "source_line_id")
    private Long sourceLineId;

    @Column(name = "source_effect_key", length = 120)
    private String sourceEffectKey;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
