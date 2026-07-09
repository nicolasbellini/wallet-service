package com.recargapay.walletservice.application.dto;

import com.recargapay.walletservice.domain.model.Money;
import com.recargapay.walletservice.domain.model.WalletId;

public record TransferCommand(WalletId sourceWalletId, WalletId destinationWalletId, Money amount) {
}
