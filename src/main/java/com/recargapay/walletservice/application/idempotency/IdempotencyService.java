package com.recargapay.walletservice.application.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Makes a mutating endpoint safe to retry. If the caller supplies an
 * {@code Idempotency-Key} header, the key and the eventual response are
 * stored in the *same transaction* as the wrapped action, so a retried
 * request with the same key returns the original response instead of
 * re-executing (e.g. double-depositing). No header means no dedupe — the
 * request runs normally, exactly as before this feature existed.
 */
@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IdempotencyService(IdempotencyKeyRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public <T> ResponseEntity<T> executeIdempotent(String idempotencyKey, String requestPath, Class<T> responseType,
                                                     Supplier<ResponseEntity<T>> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }

        Optional<IdempotencyRecord> existing = repository.findByKeyForUpdate(idempotencyKey);

        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (!record.requestPath().equals(requestPath)) {
                throw new IdempotencyKeyReusedException(idempotencyKey);
            }
            if (record.isCompleted()) {
                return ResponseEntity.status(record.responseStatus()).body(readValue(record.responseBody(), responseType));
            }
            // Reserved but never completed (the original request's process likely crashed
            // mid-flight) — fall through and treat this as a fresh attempt.
        } else {
            reserve(idempotencyKey, requestPath);
        }

        ResponseEntity<T> result = action.get();
        repository.complete(idempotencyKey, result.getStatusCode().value(), writeValue(result.getBody()));
        return result;
    }

    private void reserve(String key, String requestPath) {
        try {
            repository.reserve(key, requestPath, Instant.now(clock));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new IdempotencyKeyInFlightException(key);
        }
    }

    private <T> T readValue(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize cached idempotent response", e);
        }
    }

    private String writeValue(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotent response", e);
        }
    }
}
