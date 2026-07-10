package com.recargapay.walletservice.adapters.out.persistence.mapper;

import com.recargapay.walletservice.adapters.out.persistence.entity.WalletJpaEntity;
import com.recargapay.walletservice.domain.model.Money;
import com.recargapay.walletservice.domain.model.UserId;
import com.recargapay.walletservice.domain.model.Wallet;
import com.recargapay.walletservice.domain.model.WalletId;
import org.springframework.stereotype.Component;

import java.util.Currency;

@Component
public class WalletMapper {

    public WalletJpaEntity toEntity(Wallet wallet) {
        return new WalletJpaEntity(
                wallet.id().value(),
                wallet.userId().value(),
                wallet.balance().currency().getCurrencyCode(),
                wallet.balance().amount(),
                wallet.createdAt(),
                null
        );
    }

    public Wallet toDomain(WalletJpaEntity entity) {
        Currency currency = Currency.getInstance(entity.getCurrency());
        return new Wallet(
                new WalletId(entity.getId()),
                new UserId(entity.getUserId()),
                new Money(entity.getBalance(), currency),
                entity.getCreatedAt()
        );
    }
}
