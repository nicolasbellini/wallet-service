package com.recargapay.walletservice.adapters.in.web.dto;

import com.recargapay.walletservice.application.dto.BalanceView;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BalanceResponse(UUID walletId, BigDecimal balance, String currency, Instant asOf) {

    public static BalanceResponse from(BalanceView view) {
        return new BalanceResponse(
                view.walletId().value(),
                view.balance().amount(),
                view.balance().currency().getCurrencyCode(),
                view.asOf());
    }
}
