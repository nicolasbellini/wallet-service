package com.recargapay.walletservice.adapters.out.persistence.repository;

import com.recargapay.walletservice.adapters.out.persistence.entity.LedgerEntryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface LedgerEntryJpaRepository extends JpaRepository<LedgerEntryJpaEntity, UUID> {

    Optional<LedgerEntryJpaEntity> findFirstByWalletIdAndOccurredAtLessThanEqualOrderByOccurredAtDescIdDesc(
            UUID walletId, Instant asOf);
}
