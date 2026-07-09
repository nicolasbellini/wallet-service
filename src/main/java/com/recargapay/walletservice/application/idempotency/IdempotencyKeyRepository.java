package com.recargapay.walletservice.application.idempotency;

import java.time.Instant;
import java.util.Optional;

public interface IdempotencyKeyRepository {

    /**
     * Reads the record with a write lock held until the enclosing transaction
     * commits, serializing concurrent requests carrying the same key.
     */
    Optional<IdempotencyRecord> findByKeyForUpdate(String key);

    /**
     * Claims a key before the wrapped action runs. Throws
     * {@link IdempotencyKeyInFlightException} if another transaction
     * concurrently reserved the same key first (a genuine race between two
     * simultaneous requests using the same key, as opposed to a sequential
     * retry — {@code findByKeyForUpdate} handles the sequential-retry case).
     */
    IdempotencyRecord reserve(String key, String requestPath, Instant now);

    void complete(String key, int status, String body);
}
