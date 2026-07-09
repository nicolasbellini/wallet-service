package com.recargapay.walletservice.domain.exception;

import com.recargapay.walletservice.domain.model.WalletId;

public class SameWalletTransferException extends RuntimeException {

    public SameWalletTransferException(WalletId walletId) {
        super("Cannot transfer to the same wallet: " + walletId);
    }
}
