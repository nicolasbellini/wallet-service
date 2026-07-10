package com.recargapay.walletservice.adapters.out.persistence;

import com.recargapay.walletservice.adapters.out.persistence.mapper.LedgerEntryMapper;
import com.recargapay.walletservice.adapters.out.persistence.repository.LedgerEntryJpaRepository;
import com.recargapay.walletservice.domain.model.LedgerEntry;
import com.recargapay.walletservice.domain.model.WalletId;
import com.recargapay.walletservice.domain.port.LedgerRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class LedgerRepositoryAdapter implements LedgerRepository {

    private final LedgerEntryJpaRepository jpaRepository;
    private final LedgerEntryMapper mapper;

    public LedgerRepositoryAdapter(LedgerEntryJpaRepository jpaRepository, LedgerEntryMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public LedgerEntry append(LedgerEntry entry) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(entry)));
    }

    @Override
    public Optional<LedgerEntry> findLatestAsOf(WalletId walletId, Instant asOf) {
        return jpaRepository
                .findFirstByWalletIdAndOccurredAtLessThanEqualOrderByOccurredAtDescIdDesc(walletId.value(), asOf)
                .map(mapper::toDomain);
    }
}
