package com.recargapay.walletservice.domain.port;

import com.recargapay.walletservice.domain.model.UserId;
import com.recargapay.walletservice.domain.model.Wallet;
import com.recargapay.walletservice.domain.model.WalletId;

import java.util.Optional;

public interface WalletRepository {

    Wallet save(Wallet wallet);

    Optional<Wallet> findById(WalletId walletId);

    /**
     * Reads the wallet row with a write lock held until the enclosing
     * transaction commits, serializing concurrent mutations to this wallet.
     * Must be used by every use case that deposits, withdraws, or transfers.
     */
    Optional<Wallet> findByIdForUpdate(WalletId walletId);

    Optional<Wallet> findByUserId(UserId userId);
}
