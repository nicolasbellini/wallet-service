package com.recargapay.walletservice.application.usecase;

import com.recargapay.walletservice.application.dto.DepositCommand;
import com.recargapay.walletservice.application.dto.DepositResult;
import com.recargapay.walletservice.domain.exception.WalletNotFoundException;
import com.recargapay.walletservice.domain.model.LedgerEntry;
import com.recargapay.walletservice.domain.model.Wallet;
import com.recargapay.walletservice.domain.port.LedgerRepository;
import com.recargapay.walletservice.domain.port.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class DepositUseCase {

    private final WalletRepository walletRepository;
    private final LedgerRepository ledgerRepository;
    private final Clock clock;

    public DepositUseCase(WalletRepository walletRepository, LedgerRepository ledgerRepository, Clock clock) {
        this.walletRepository = walletRepository;
        this.ledgerRepository = ledgerRepository;
        this.clock = clock;
    }

    @Transactional
    public DepositResult execute(DepositCommand command) {
        Wallet wallet = walletRepository.findByIdForUpdate(command.walletId())
                .orElseThrow(() -> new WalletNotFoundException(command.walletId()));

        Wallet updated = wallet.deposit(command.amount());
        walletRepository.save(updated);

        LedgerEntry entry = LedgerEntry.deposit(
                updated.id(), command.amount(), updated.balance(), Instant.now(clock), command.reference());
        ledgerRepository.append(entry);

        return new DepositResult(updated.id(), entry.id(), updated.balance());
    }
}
