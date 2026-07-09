package com.recargapay.walletservice.application.usecase;

import com.recargapay.walletservice.application.dto.CreateWalletCommand;
import com.recargapay.walletservice.application.dto.WalletView;
import com.recargapay.walletservice.domain.exception.DuplicateWalletException;
import com.recargapay.walletservice.domain.model.SupportedCurrency;
import com.recargapay.walletservice.domain.model.Wallet;
import com.recargapay.walletservice.domain.model.WalletId;
import com.recargapay.walletservice.domain.port.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class CreateWalletUseCase {

    private final WalletRepository walletRepository;
    private final Clock clock;

    public CreateWalletUseCase(WalletRepository walletRepository, Clock clock) {
        this.walletRepository = walletRepository;
        this.clock = clock;
    }

    @Transactional
    public WalletView execute(CreateWalletCommand command) {
        walletRepository.findByUserId(command.userId()).ifPresent(existing -> {
            throw new DuplicateWalletException(command.userId());
        });

        Wallet wallet = Wallet.createNew(WalletId.generate(), command.userId(), SupportedCurrency.BRL, Instant.now(clock));
        Wallet saved = walletRepository.save(wallet);
        return WalletView.from(saved);
    }
}
