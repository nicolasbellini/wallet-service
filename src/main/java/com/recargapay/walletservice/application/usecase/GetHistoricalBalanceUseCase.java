package com.recargapay.walletservice.application.usecase;

import com.recargapay.walletservice.application.dto.BalanceView;
import com.recargapay.walletservice.domain.exception.WalletNotFoundException;
import com.recargapay.walletservice.domain.model.LedgerEntry;
import com.recargapay.walletservice.domain.model.Money;
import com.recargapay.walletservice.domain.model.Wallet;
import com.recargapay.walletservice.domain.model.WalletId;
import com.recargapay.walletservice.domain.port.LedgerRepository;
import com.recargapay.walletservice.domain.port.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class GetHistoricalBalanceUseCase {

    private final WalletRepository walletRepository;
    private final LedgerRepository ledgerRepository;

    public GetHistoricalBalanceUseCase(WalletRepository walletRepository, LedgerRepository ledgerRepository) {
        this.walletRepository = walletRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional(readOnly = true)
    public BalanceView execute(WalletId walletId, Instant asOf) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));

        Money balance = ledgerRepository.findLatestAsOf(walletId, asOf)
                .map(LedgerEntry::balanceAfter)
                .orElseGet(() -> Money.zero(wallet.balance().currency()));

        return new BalanceView(walletId, balance, asOf);
    }
}
