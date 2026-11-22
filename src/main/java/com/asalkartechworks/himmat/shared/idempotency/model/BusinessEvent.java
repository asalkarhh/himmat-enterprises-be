package com.asalkartechworks.himmat.shared.idempotency.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "business_event")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "request_key", nullable = false, unique = true, length = 100)
    private String requestKey;

    @Column(name = "request_hash", nullable = false, length = 128)
    private String requestHash;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "reverses_event_id")
    private Long reversesEventId;

    @Column(name = "reason")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "audit_payload", nullable = false, columnDefinition = "jsonb")
    private String auditPayload;
}
