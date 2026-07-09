package com.recargapay.walletservice.adapters.out.persistence.repository;

import com.recargapay.walletservice.adapters.out.persistence.entity.IdempotencyKeyJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyJpaEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from IdempotencyKeyJpaEntity k where k.key = :key")
    Optional<IdempotencyKeyJpaEntity> findByKeyForUpdate(@Param("key") String key);
}
