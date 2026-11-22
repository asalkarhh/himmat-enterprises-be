package com.asalkartechworks.himmat.shared.idempotency.service;

import com.asalkartechworks.himmat.common.errors.AppException;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.shared.idempotency.model.BusinessEvent;
import com.asalkartechworks.himmat.shared.idempotency.repository.BusinessEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final BusinessEventRepository businessEventRepository;
    private final ObjectMapper objectMapper;
    private final BusinessClock businessClock;

    @Transactional
    public <T> T executeIdempotent(String idempotencyKey,
                                   Long actorId,
                                   String httpMethod,
                                   String route,
                                   Object requestBody,
                                   Class<T> responseType,
                                   Supplier<T> commandAction) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return commandAction.get();
        }

        String sanitizedKey = idempotencyKey.trim();
        String requestHash = computeHash(httpMethod, route, actorId, requestBody);

        // Transaction-scoped advisory lock serializes concurrent matching request keys
        businessEventRepository.acquireAdvisoryLock(sanitizedKey);

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

        T result = commandAction.get();

        try {
            String sanitizedPayload = objectMapper.writeValueAsString(result);
            BusinessEvent event = BusinessEvent.builder()
                    .eventType("COMMAND")
                    .requestKey(sanitizedKey)
                    .requestHash(requestHash)
                    .actorId(actorId)
                    .occurredAt(businessClock.now())
                    .auditPayload(sanitizedPayload)
                    .build();
            businessEventRepository.save(event);
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
