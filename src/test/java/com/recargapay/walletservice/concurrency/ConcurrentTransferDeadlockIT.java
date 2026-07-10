package com.recargapay.walletservice.concurrency;

import com.recargapay.walletservice.application.dto.*;
import com.recargapay.walletservice.application.usecase.CreateWalletUseCase;
import com.recargapay.walletservice.application.usecase.DepositUseCase;
import com.recargapay.walletservice.application.usecase.GetCurrentBalanceUseCase;
import com.recargapay.walletservice.application.usecase.TransferFundsUseCase;
import com.recargapay.walletservice.domain.model.Money;
import com.recargapay.walletservice.domain.model.SupportedCurrency;
import com.recargapay.walletservice.domain.model.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the deadlock-prevention strategy: {@code TransferFundsUseCase} locks
 * wallets in ascending-id order regardless of source/destination role, so
 * simultaneous opposite-direction transfers (A->B and B->A) must never
 * deadlock or time out waiting on each other's locks.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrentTransferDeadlockIT {

    @Autowired
    private CreateWalletUseCase createWalletUseCase;

    @Autowired
    private DepositUseCase depositUseCase;

    @Autowired
    private TransferFundsUseCase transferFundsUseCase;

    @Autowired
    private GetCurrentBalanceUseCase getCurrentBalanceUseCase;

    @Test
    @Timeout(30)
    void oppositeDirectionTransfersDoNotDeadlock() throws Exception {
        WalletView walletA = createWalletUseCase.execute(new CreateWalletCommand(new UserId(UUID.randomUUID())));
        WalletView walletB = createWalletUseCase.execute(new CreateWalletCommand(new UserId(UUID.randomUUID())));

        depositUseCase.execute(new DepositCommand(walletA.walletId(), Money.of("10000.00", SupportedCurrency.BRL), null));
        depositUseCase.execute(new DepositCommand(walletB.walletId(), Money.of("10000.00", SupportedCurrency.BRL), null));

        int iterations = 50;
        CountDownLatch startLatch = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < iterations; i++) {
                futures.add(executor.submit(() -> {
                    startLatch.await();
                    transferFundsUseCase.execute(new TransferCommand(walletA.walletId(), walletB.walletId(), Money.of("1.00", SupportedCurrency.BRL)));
                    return null;
                }));
                futures.add(executor.submit(() -> {
                    startLatch.await();
                    transferFundsUseCase.execute(new TransferCommand(walletB.walletId(), walletA.walletId(), Money.of("1.00", SupportedCurrency.BRL)));
                    return null;
                }));
            }

            startLatch.countDown();
            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        }

        // Equal numbers of transfers in each direction net out to the starting balances.
        BalanceView balanceA = getCurrentBalanceUseCase.execute(walletA.walletId());
        BalanceView balanceB = getCurrentBalanceUseCase.execute(walletB.walletId());
        assertThat(balanceA.balance().amount()).isEqualByComparingTo("10000.00");
        assertThat(balanceB.balance().amount()).isEqualByComparingTo("10000.00");
    }
}
