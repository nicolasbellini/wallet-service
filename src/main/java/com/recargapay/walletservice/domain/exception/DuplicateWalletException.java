package com.recargapay.walletservice.domain.exception;

import com.recargapay.walletservice.domain.model.UserId;

public class DuplicateWalletException extends RuntimeException {

    public DuplicateWalletException(UserId userId) {
        super("A wallet already exists for user: " + userId);
    }
}
