package com.recargapay.walletservice.application.dto;

import com.recargapay.walletservice.domain.model.Money;
import com.recargapay.walletservice.domain.model.WalletId;

public record WithdrawCommand(WalletId walletId, Money amount) {
}
