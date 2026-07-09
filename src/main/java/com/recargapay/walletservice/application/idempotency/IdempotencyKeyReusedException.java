package com.recargapay.walletservice.application.idempotency;

public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(String key) {
        super("Idempotency-Key '%s' was already used for a different request".formatted(key));
    }
}
