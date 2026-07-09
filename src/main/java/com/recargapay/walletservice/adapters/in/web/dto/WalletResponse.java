package com.recargapay.walletservice.adapters.in.web.dto;

import com.recargapay.walletservice.application.dto.WalletView;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletResponse(UUID walletId, UUID userId, String currency, BigDecimal balance, Instant createdAt) {

    public static WalletResponse from(WalletView view) {
        return new WalletResponse(
                view.walletId().value(),
                view.userId().value(),
                view.balance().currency().getCurrencyCode(),
                view.balance().amount(),
                view.createdAt());
    }
}
