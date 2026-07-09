package com.recargapay.walletservice.application.usecase;

import com.recargapay.walletservice.application.dto.TransferCommand;
import com.recargapay.walletservice.application.dto.TransferResult;
import com.recargapay.walletservice.domain.exception.SameWalletTransferException;
import com.recargapay.walletservice.domain.exception.WalletNotFoundException;
import com.recargapay.walletservice.domain.model.LedgerEntry;
import com.recargapay.walletservice.domain.model.Wallet;
import com.recargapay.walletservice.domain.model.WalletId;
import com.recargapay.walletservice.domain.port.LedgerRepository;
import com.recargapay.walletservice.domain.port.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Transfers are wallet-to-wallet within this application only — both wallets
 * must already exist here. There is no external/third-party transfer target
 * and no pending/confirmation status: the whole operation is atomic and
 * synchronous within a single transaction.
 */
@Service
public class TransferFundsUseCase {

    private final WalletRepository walletRepository;
    private final LedgerRepository ledgerRepository;
    private final Clock clock;

    public TransferFundsUseCase(WalletRepository walletRepository, LedgerRepository ledgerRepository, Clock clock) {
        this.walletRepository = walletRepository;
        this.ledgerRepository = ledgerRepository;
        this.clock = clock;
    }

    @Transactional
    public TransferResult execute(TransferCommand command) {
        WalletId sourceId = command.sourceWalletId();
        WalletId destinationId = command.destinationWalletId();

        if (sourceId.equals(destinationId)) {
            throw new SameWalletTransferException(sourceId);
        }

        // Lock in a deterministic order (ascending id) regardless of source/destination
        // role, so two concurrent opposite transfers (A->B and B->A) can never deadlock.
        WalletId firstToLock = sourceId.value().compareTo(destinationId.value()) <= 0 ? sourceId : destinationId;
        WalletId secondToLock = firstToLock.equals(sourceId) ? destinationId : sourceId;

        Wallet firstLocked = walletRepository.findByIdForUpdate(firstToLock)
                .orElseThrow(() -> new WalletNotFoundException(firstToLock));
        Wallet secondLocked = walletRepository.findByIdForUpdate(secondToLock)
                .orElseThrow(() -> new WalletNotFoundException(secondToLock));

        Wallet sourceWallet = firstLocked.id().equals(sourceId) ? firstLocked : secondLocked;
        Wallet destinationWallet = firstLocked.id().equals(sourceId) ? secondLocked : firstLocked;

        Wallet updatedSource = sourceWallet.withdraw(command.amount());
        Wallet updatedDestination = destinationWallet.deposit(command.amount());

        walletRepository.save(updatedSource);
        walletRepository.save(updatedDestination);

        UUID transferId = UUID.randomUUID();
        Instant now = Instant.now(clock);

        LedgerEntry outEntry = LedgerEntry.transferOut(
                updatedSource.id(), updatedDestination.id(), command.amount(), updatedSource.balance(), now, transferId);
        LedgerEntry inEntry = LedgerEntry.transferIn(
                updatedDestination.id(), updatedSource.id(), command.amount(), updatedDestination.balance(), now, transferId);

        ledgerRepository.append(outEntry);
        ledgerRepository.append(inEntry);

        return new TransferResult(
                transferId,
                updatedSource.id(),
                updatedDestination.id(),
                command.amount(),
                updatedSource.balance(),
                updatedDestination.balance());
    }
}
