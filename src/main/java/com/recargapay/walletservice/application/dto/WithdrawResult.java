package com.recargapay.walletservice.application.dto;

import com.recargapay.walletservice.domain.model.Money;
import com.recargapay.walletservice.domain.model.WalletId;

import java.util.UUID;

public record WithdrawResult(WalletId walletId, UUID entryId, Money newBalance) {
}
