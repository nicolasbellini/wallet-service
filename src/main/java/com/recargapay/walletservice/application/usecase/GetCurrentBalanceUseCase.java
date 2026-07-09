package com.recargapay.walletservice.application.usecase;

import com.recargapay.walletservice.application.dto.BalanceView;
import com.recargapay.walletservice.domain.exception.WalletNotFoundException;
import com.recargapay.walletservice.domain.model.Wallet;
import com.recargapay.walletservice.domain.model.WalletId;
import com.recargapay.walletservice.domain.port.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetCurrentBalanceUseCase {

    private final WalletRepository walletRepository;

    public GetCurrentBalanceUseCase(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Transactional(readOnly = true)
    public BalanceView execute(WalletId walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
        return new BalanceView(wallet.id(), wallet.balance(), null);
    }
}
