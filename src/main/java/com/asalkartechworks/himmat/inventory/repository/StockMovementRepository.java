package com.asalkartechworks.himmat.inventory.repository;

import com.asalkartechworks.himmat.inventory.model.StockMovement;
import com.asalkartechworks.himmat.inventory.model.StockMovementDirection;
import com.asalkartechworks.himmat.inventory.model.StockMovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    boolean existsBySourceEffectKey(String sourceEffectKey);

    @Query(value = "SELECT sm FROM StockMovement sm " +
            "JOIN FETCH sm.variant v " +
            "JOIN FETCH v.product p " +
            "LEFT JOIN FETCH sm.businessEvent be " +
            "WHERE (:variantId IS NULL OR sm.variant.id = :variantId) " +
            "AND (:movementType IS NULL OR sm.movementType = :movementType) " +
            "AND (:direction IS NULL OR sm.direction = :direction) " +
            "AND (:sourceEntityType IS NULL OR sm.sourceEntityType = :sourceEntityType) " +
            "AND (:businessEventId IS NULL OR sm.businessEvent.id = :businessEventId) " +
            "AND (cast(:startInstant as timestamp) IS NULL OR sm.occurredAt >= :startInstant) " +
            "AND (cast(:endInstant as timestamp) IS NULL OR sm.occurredAt < :endInstant)",
            countQuery = "SELECT COUNT(sm) FROM StockMovement sm " +
                    "WHERE (:variantId IS NULL OR sm.variant.id = :variantId) " +
                    "AND (:movementType IS NULL OR sm.movementType = :movementType) " +
                    "AND (:direction IS NULL OR sm.direction = :direction) " +
                    "AND (:sourceEntityType IS NULL OR sm.sourceEntityType = :sourceEntityType) " +
                    "AND (:businessEventId IS NULL OR sm.businessEvent.id = :businessEventId) " +
                    "AND (cast(:startInstant as timestamp) IS NULL OR sm.occurredAt >= :startInstant) " +
                    "AND (cast(:endInstant as timestamp) IS NULL OR sm.occurredAt < :endInstant)")
    Page<StockMovement> searchMovements(@Param("variantId") Long variantId,
                                       @Param("movementType") StockMovementType movementType,
                                       @Param("direction") StockMovementDirection direction,
                                       @Param("sourceEntityType") String sourceEntityType,
                                       @Param("businessEventId") Long businessEventId,
                                       @Param("startInstant") Instant startInstant,
                                       @Param("endInstant") Instant endInstant,
                                       Pageable pageable);
}
