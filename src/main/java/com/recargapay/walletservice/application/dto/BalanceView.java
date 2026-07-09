package com.recargapay.walletservice.application.dto;

import com.recargapay.walletservice.domain.model.Money;
import com.recargapay.walletservice.domain.model.WalletId;

import java.time.Instant;

/** {@code asOf} is null for a current-balance query, non-null for a historical one. */
public record BalanceView(WalletId walletId, Money balance, Instant asOf) {
}
