package com.recargapay.walletservice.domain.model;

import java.util.Objects;
import java.util.UUID;

public record WalletId(UUID value) {

    public WalletId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static WalletId generate() {
        return new WalletId(UUID.randomUUID());
    }

    public static WalletId of(String value) {
        return new WalletId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
