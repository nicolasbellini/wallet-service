package com.recargapay.walletservice.domain.model;

import com.recargapay.walletservice.domain.exception.InvalidAmountException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable fact recorded in the append-only ledger. {@code balanceAfter} is
 * a snapshot of the wallet balance immediately after this entry was applied,
 * which lets historical-balance queries find the answer with a single lookup
 * (latest entry at/before the requested instant) instead of replaying the
 * whole history from genesis.
 */
public record LedgerEntry(
        UUID id,
        WalletId walletId,
        EntryType entryType,
        Money amount,
        Money balanceAfter,
        Instant occurredAt,
        UUID transferId,
        WalletId relatedWalletId,
        String reference
) {

    public LedgerEntry {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(walletId, "walletId must not be null");
        Objects.requireNonNull(entryType, "entryType must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(balanceAfter, "balanceAfter must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (!amount.isPositive()) {
            throw new InvalidAmountException("ledger entry amount must be positive: " + amount.amount());
        }
        boolean isTransfer = entryType == EntryType.TRANSFER_OUT || entryType == EntryType.TRANSFER_IN;
        if (isTransfer && (transferId == null || relatedWalletId == null)) {
            throw new IllegalArgumentException("transfer entries require transferId and relatedWalletId");
        }
    }

    public static LedgerEntry deposit(WalletId walletId, Money amount, Money balanceAfter, Instant occurredAt, String reference) {
        return new LedgerEntry(UUID.randomUUID(), walletId, EntryType.DEPOSIT, amount, balanceAfter, occurredAt, null, null, reference);
    }

    public static LedgerEntry withdrawal(WalletId walletId, Money amount, Money balanceAfter, Instant occurredAt, String reference) {
        return new LedgerEntry(UUID.randomUUID(), walletId, EntryType.WITHDRAWAL, amount, balanceAfter, occurredAt, null, null, reference);
    }

    public static LedgerEntry transferOut(WalletId walletId, WalletId destinationWalletId, Money amount, Money balanceAfter, Instant occurredAt, UUID transferId) {
        return new LedgerEntry(UUID.randomUUID(), walletId, EntryType.TRANSFER_OUT, amount, balanceAfter, occurredAt, transferId, destinationWalletId, null);
    }

    public static LedgerEntry transferIn(WalletId walletId, WalletId sourceWalletId, Money amount, Money balanceAfter, Instant occurredAt, UUID transferId) {
        return new LedgerEntry(UUID.randomUUID(), walletId, EntryType.TRANSFER_IN, amount, balanceAfter, occurredAt, transferId, sourceWalletId, null);
    }
}
