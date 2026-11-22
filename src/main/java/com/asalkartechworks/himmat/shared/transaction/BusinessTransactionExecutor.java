package com.asalkartechworks.himmat.shared.transaction;

import com.asalkartechworks.himmat.common.errors.AppException;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.shared.idempotency.model.BusinessEvent;
import com.asalkartechworks.himmat.shared.idempotency.repository.BusinessEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Shared transactional foundation for business events and durable idempotency.
 * Coordinates database effects across business documents, inventory balances,
 * stock movements, and future ledger entries within a single atomic database transaction.
 *
 * STANDARD LOCK ORDERING TO PREVENT DEADLOCKS:
 * 1. Party Balances (Customer / Supplier accounts, ordered by party ID ASC)
 * 2. Document Headers & Lines (Purchase Invoices, Sales Bills)
 * 3. Inventory Balances (inventory_balance, ordered by variant_id ASC)
 * 4. Audit & Stock Movements
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessTransactionExecutor {

    private final BusinessEventRepository businessEventRepository;
    private final ObjectMapper objectMapper;
    private final BusinessClock businessClock;

    @Transactional(propagation = Propagation.REQUIRED)
    public <T> T executeTransaction(String idempotencyKey,
                                   Long actorId,
                                   String eventType,
                                   String reason,
                                   String httpMethod,
                                   String route,
                                   Object requestBody,
                                   Class<T> responseType,
                                   Function<BusinessEvent, T> transactionAction) {
        boolean isIdempotentRequest = idempotencyKey != null && !idempotencyKey.isBlank();
        String sanitizedKey = isIdempotentRequest ? idempotencyKey.trim() : "INTERNAL-" + UUID.randomUUID();
        String requestHash = computeHash(httpMethod, route, actorId, requestBody);

        // Acquire PostgreSQL transaction-scoped advisory lock on request key
        businessEventRepository.acquireAdvisoryLock(sanitizedKey);

        if (isIdempotentRequest) {
            Optional<BusinessEvent> existingOpt = businessEventRepository.findByRequestKey(sanitizedKey);
            if (existingOpt.isPresent()) {
                BusinessEvent existing = existingOpt.get();
                if (existing.getRequestHash().equals(requestHash) && existing.getActorId().equals(actorId)) {
                    log.info("Replaying previously committed idempotent response for key: {}", sanitizedKey);
                    try {
                        return objectMapper.readValue(existing.getAuditPayload(), responseType);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize saved audit payload for key: {}", sanitizedKey, e);
                        throw new AppException("INTERNAL_ERROR", "Failed to deserialize cached response.", HttpStatus.INTERNAL_SERVER_ERROR);
                    }
                } else {
                    log.warn("Idempotency key mismatch detected for key: {}", sanitizedKey);
                    throw new AppException(
                            "IDEMPOTENCY_CONFLICT",
                            "Idempotency-Key '" + sanitizedKey + "' was previously submitted with a different payload or actor.",
                            HttpStatus.CONFLICT
                    );
                }
            }
        }

        // Create business event to link with movements/records in the same transaction
        BusinessEvent event = BusinessEvent.builder()
                .eventType(eventType)
                .requestKey(sanitizedKey)
                .requestHash(requestHash)
                .actorId(actorId)
                .occurredAt(businessClock.now())
                .reason(reason)
                .auditPayload("{}")
                .build();

        BusinessEvent savedEvent = businessEventRepository.save(event);

        // Execute caller's transactional business logic
        T result = transactionAction.apply(savedEvent);

        // Serialize and save the final response into the business event
        try {
            String serializedPayload = objectMapper.writeValueAsString(result);
            savedEvent.setAuditPayload(serializedPayload);
            businessEventRepository.save(savedEvent);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize result for audit payload: {}", sanitizedKey, e);
        }

        return result;
    }

    private String computeHash(String method, String route, Long actorId, Object body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String bodyJson = body != null ? objectMapper.writeValueAsString(body) : "";
            String rawString = (method != null ? method.toUpperCase() : "") + "|"
                    + (route != null ? route : "") + "|"
                    + (actorId != null ? actorId : "") + "|"
                    + bodyJson;
            byte[] hash = digest.digest(rawString.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new RuntimeException("Failed to calculate canonical request hash", e);
        }
    }
}
