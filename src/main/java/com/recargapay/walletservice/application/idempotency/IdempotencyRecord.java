package com.recargapay.walletservice.application.idempotency;

import java.time.Instant;

/**
 * A stored idempotent request. {@code responseStatus}/{@code responseBody}
 * are null while the original request is still being processed (the row was
 * reserved but not yet completed).
 */
public record IdempotencyRecord(
        String key,
        String requestPath,
        Integer responseStatus,
        String responseBody,
        Instant createdAt
) {

    public boolean isCompleted() {
        return responseStatus != null;
    }
}
