package com.recargapay.walletservice.domain.exception;

import com.recargapay.walletservice.domain.model.WalletId;

public class WalletNotFoundException extends RuntimeException {

    public WalletNotFoundException(WalletId walletId) {
        super("Wallet not found: " + walletId);
    }
}
