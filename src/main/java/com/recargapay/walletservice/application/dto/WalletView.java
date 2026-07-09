package com.recargapay.walletservice.application.dto;

import com.recargapay.walletservice.domain.model.Money;
import com.recargapay.walletservice.domain.model.UserId;
import com.recargapay.walletservice.domain.model.Wallet;
import com.recargapay.walletservice.domain.model.WalletId;

import java.time.Instant;

public record WalletView(WalletId walletId, UserId userId, Money balance, Instant createdAt) {

    public static WalletView from(Wallet wallet) {
        return new WalletView(wallet.id(), wallet.userId(), wallet.balance(), wallet.createdAt());
    }
}
