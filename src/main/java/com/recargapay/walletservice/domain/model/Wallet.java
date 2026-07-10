package com.recargapay.walletservice.domain.model;

import com.recargapay.walletservice.domain.exception.InsufficientFundsException;
import com.recargapay.walletservice.domain.exception.InvalidAmountException;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

public record Wallet(WalletId id, UserId userId, Money balance, Instant createdAt) {

    public Wallet(WalletId id, UserId userId, Money balance, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.balance = Objects.requireNonNull(balance, "balance must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static Wallet createNew(WalletId id, UserId userId, Currency currency, Instant createdAt) {
        return new Wallet(id, userId, Money.zero(currency), createdAt);
    }

    public Wallet deposit(Money amount) {
        requirePositive(amount);
        return new Wallet(id, userId, balance.add(amount), createdAt);
    }

    public Wallet withdraw(Money amount) {
        requirePositive(amount);
        if (balance.isLessThan(amount)) {
            throw new InsufficientFundsException(id, amount, balance);
        }
        return new Wallet(id, userId, balance.subtract(amount), createdAt);
    }

    private static void requirePositive(Money amount) {
        if (!amount.isPositive()) {
            throw new InvalidAmountException("amount must be positive: " + amount.amount());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Wallet other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Wallet[id=%s, userId=%s, balance=%s, createdAt=%s]".formatted(id, userId, balance, createdAt);
    }
}
