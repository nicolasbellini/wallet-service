package com.recargapay.walletservice.adapters.out.persistence;

import com.recargapay.walletservice.adapters.out.persistence.entity.WalletJpaEntity;
import com.recargapay.walletservice.adapters.out.persistence.mapper.WalletMapper;
import com.recargapay.walletservice.adapters.out.persistence.repository.WalletJpaRepository;
import com.recargapay.walletservice.domain.model.UserId;
import com.recargapay.walletservice.domain.model.Wallet;
import com.recargapay.walletservice.domain.model.WalletId;
import com.recargapay.walletservice.domain.port.WalletRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class WalletRepositoryAdapter implements WalletRepository {

    private final WalletJpaRepository jpaRepository;
    private final WalletMapper mapper;

    public WalletRepositoryAdapter(WalletJpaRepository jpaRepository, WalletMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Wallet save(Wallet wallet) {
        // Reuse the managed entity already tracked in this transaction's persistence
        // context (e.g. loaded via findByIdForUpdate) instead of merging a detached
        // copy, so the write goes through ordinary dirty-checking at commit.
        WalletJpaEntity entity = jpaRepository.findById(wallet.id().value())
                .map(existing -> {
                    existing.setBalance(wallet.balance().amount());
                    return existing;
                })
                .orElseGet(() -> mapper.toEntity(wallet));
        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<Wallet> findById(WalletId walletId) {
        return jpaRepository.findById(walletId.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Wallet> findByIdForUpdate(WalletId walletId) {
        return jpaRepository.findByIdForUpdate(walletId.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Wallet> findByUserId(UserId userId) {
        return jpaRepository.findByUserId(userId.value()).map(mapper::toDomain);
    }
}
