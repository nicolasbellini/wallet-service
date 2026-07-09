package com.recargapay.walletservice.application.dto;

import com.recargapay.walletservice.domain.model.Money;
import com.recargapay.walletservice.domain.model.WalletId;

public record DepositCommand(WalletId walletId, Money amount, String reference) {
}
