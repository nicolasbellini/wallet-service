package com.recargapay.walletservice.application.dto;

import com.recargapay.walletservice.domain.model.Money;
import com.recargapay.walletservice.domain.model.WalletId;

import java.util.UUID;

public record TransferResult(
        UUID transferId,
        WalletId sourceWalletId,
        WalletId destinationWalletId,
        Money amount,
        Money sourceBalanceAfter,
        Money destinationBalanceAfter
) {
}
