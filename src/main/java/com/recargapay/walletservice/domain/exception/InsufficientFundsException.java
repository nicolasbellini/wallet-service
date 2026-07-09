package com.recargapay.walletservice.domain.exception;

import com.recargapay.walletservice.domain.model.Money;
import com.recargapay.walletservice.domain.model.WalletId;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(WalletId walletId, Money requested, Money available) {
        super("Wallet %s has insufficient funds: requested %s but only %s available"
                .formatted(walletId, requested.amount(), available.amount()));
    }
}
