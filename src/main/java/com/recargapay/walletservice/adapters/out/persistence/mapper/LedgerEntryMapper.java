package com.recargapay.walletservice.adapters.out.persistence.mapper;

import com.recargapay.walletservice.adapters.out.persistence.entity.LedgerEntryJpaEntity;
import com.recargapay.walletservice.domain.model.EntryType;
import com.recargapay.walletservice.domain.model.LedgerEntry;
import com.recargapay.walletservice.domain.model.Money;
import com.recargapay.walletservice.domain.model.SupportedCurrency;
import com.recargapay.walletservice.domain.model.WalletId;
import org.springframework.stereotype.Component;

@Component
public class LedgerEntryMapper {

    public LedgerEntryJpaEntity toEntity(LedgerEntry entry) {
        return new LedgerEntryJpaEntity(
                entry.id(),
                entry.walletId().value(),
                entry.entryType().name(),
                entry.amount().amount(),
                entry.balanceAfter().amount(),
                entry.occurredAt(),
                entry.transferId(),
                entry.relatedWalletId() != null ? entry.relatedWalletId().value() : null,
                entry.reference()
        );
    }

    public LedgerEntry toDomain(LedgerEntryJpaEntity entity) {
        return new LedgerEntry(
                entity.getId(),
                new WalletId(entity.getWalletId()),
                EntryType.valueOf(entity.getEntryType()),
                new Money(entity.getAmount(), SupportedCurrency.BRL),
                new Money(entity.getBalanceAfter(), SupportedCurrency.BRL),
                entity.getOccurredAt(),
                entity.getTransferId(),
                entity.getRelatedWalletId() != null ? new WalletId(entity.getRelatedWalletId()) : null,
                entity.getReference()
        );
    }
}
