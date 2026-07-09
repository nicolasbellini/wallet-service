package com.recargapay.walletservice.domain.port;

import com.recargapay.walletservice.domain.model.LedgerEntry;
import com.recargapay.walletservice.domain.model.WalletId;

import java.time.Instant;
import java.util.Optional;

public interface LedgerRepository {

    LedgerEntry append(LedgerEntry entry);

    /**
     * The latest entry for this wallet at or before {@code asOf}. Its
     * {@code balanceAfter} is the wallet's balance at that instant.
     * Empty means the wallet had no activity at/before that instant.
     */
    Optional<LedgerEntry> findLatestAsOf(WalletId walletId, Instant asOf);
}
