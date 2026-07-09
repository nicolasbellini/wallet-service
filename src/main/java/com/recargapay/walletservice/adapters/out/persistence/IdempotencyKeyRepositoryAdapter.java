package com.recargapay.walletservice.adapters.out.persistence;

import com.recargapay.walletservice.adapters.out.persistence.entity.IdempotencyKeyJpaEntity;
import com.recargapay.walletservice.adapters.out.persistence.repository.IdempotencyKeyJpaRepository;
import com.recargapay.walletservice.application.idempotency.IdempotencyKeyRepository;
import com.recargapay.walletservice.application.idempotency.IdempotencyRecord;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class IdempotencyKeyRepositoryAdapter implements IdempotencyKeyRepository {

    private final IdempotencyKeyJpaRepository jpaRepository;

    public IdempotencyKeyRepositoryAdapter(IdempotencyKeyJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<IdempotencyRecord> findByKeyForUpdate(String key) {
        return jpaRepository.findByKeyForUpdate(key).map(this::toDomain);
    }

    @Override
    public IdempotencyRecord reserve(String key, String requestPath, Instant now) {
        // saveAndFlush forces the INSERT (and its unique-constraint check) to happen
        // right here, so a genuine concurrent-reservation race surfaces immediately
        // as a DataIntegrityViolationException instead of at end-of-transaction flush.
        IdempotencyKeyJpaEntity saved = jpaRepository.saveAndFlush(
                new IdempotencyKeyJpaEntity(key, requestPath, null, null, now));
        return toDomain(saved);
    }

    @Override
    public void complete(String key, int status, String body) {
        IdempotencyKeyJpaEntity entity = jpaRepository.findById(key)
                .orElseThrow(() -> new IllegalStateException("Idempotency key was not reserved: " + key));
        entity.setResponseStatus(status);
        entity.setResponseBody(body);
        jpaRepository.save(entity);
    }

    private IdempotencyRecord toDomain(IdempotencyKeyJpaEntity entity) {
        return new IdempotencyRecord(
                entity.getKey(), entity.getRequestPath(), entity.getResponseStatus(), entity.getResponseBody(), entity.getCreatedAt());
    }
}
