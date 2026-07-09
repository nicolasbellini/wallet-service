package com.recargapay.walletservice.application.idempotency;

public class IdempotencyKeyInFlightException extends RuntimeException {

    public IdempotencyKeyInFlightException(String key) {
        super("A request with Idempotency-Key '%s' is already being processed, please retry".formatted(key));
    }
}
