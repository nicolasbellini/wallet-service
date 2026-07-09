package com.recargapay.walletservice.domain.model;

import com.recargapay.walletservice.domain.exception.InsufficientFundsException;
import com.recargapay.walletservice.domain.exception.InvalidAmountException;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

/**
 * Aggregate root. Modeled as an immutable snapshot of wallet state — every
 * mutation (deposit/withdraw) returns a new instance rather than mutating in
 * place, which keeps the domain logic side-effect-free and easy to unit test.
 * Equality is identity-based (by {@code id}), not value-based, because two
 * snapshots of the same wallet at different balances are still "the same"
 * wallet — this is why Wallet is a plain class rather than a record.
 */
public final class Wallet {

    private final WalletId id;
    private final UserId userId;
    private final Money balance;
    private final Instant createdAt;

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

    public WalletId id() {
        return id;
    }

    public UserId userId() {
        return userId;
    }

    public Money balance() {
        return balance;
    }

    public Instant createdAt() {
        return createdAt;
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
