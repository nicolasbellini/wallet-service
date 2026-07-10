package com.recargapay.walletservice.adapters.out.persistence.repository;

import com.recargapay.walletservice.adapters.out.persistence.entity.WalletJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WalletJpaRepository extends JpaRepository<WalletJpaEntity, UUID> {

    Optional<WalletJpaEntity> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WalletJpaEntity w where w.id = :id")
    Optional<WalletJpaEntity> findByIdForUpdate(@Param("id") UUID id);
}
