package com.recargapay.walletservice.application.usecase;

import com.recargapay.walletservice.application.dto.WithdrawCommand;
import com.recargapay.walletservice.application.dto.WithdrawResult;
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
public class WithdrawUseCase {

    private final WalletRepository walletRepository;
    private final LedgerRepository ledgerRepository;
    private final Clock clock;

    public WithdrawUseCase(WalletRepository walletRepository, LedgerRepository ledgerRepository, Clock clock) {
        this.walletRepository = walletRepository;
        this.ledgerRepository = ledgerRepository;
        this.clock = clock;
    }

    @Transactional
    public WithdrawResult execute(WithdrawCommand command) {
        Wallet wallet = walletRepository.findByIdForUpdate(command.walletId())
                .orElseThrow(() -> new WalletNotFoundException(command.walletId()));

        Wallet updated = wallet.withdraw(command.amount());
        walletRepository.save(updated);

        LedgerEntry entry = LedgerEntry.withdrawal(
                updated.id(), command.amount(), updated.balance(), Instant.now(clock), null);
        ledgerRepository.append(entry);

        return new WithdrawResult(updated.id(), entry.id(), updated.balance());
    }
}
