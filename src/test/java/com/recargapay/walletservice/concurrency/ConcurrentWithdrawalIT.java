package com.recargapay.walletservice.concurrency;

import com.recargapay.walletservice.application.dto.*;
import com.recargapay.walletservice.application.usecase.*;
import com.recargapay.walletservice.domain.exception.InsufficientFundsException;
import com.recargapay.walletservice.domain.model.Money;
import com.recargapay.walletservice.domain.model.SupportedCurrency;
import com.recargapay.walletservice.domain.model.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the pessimistic-locking strategy: firing many concurrent withdrawals
 * against a wallet that can only satisfy half of them must never overdraw the
 * balance, regardless of thread interleaving.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrentWithdrawalIT {

    @Autowired
    private CreateWalletUseCase createWalletUseCase;

    @Autowired
    private DepositUseCase depositUseCase;

    @Autowired
    private WithdrawUseCase withdrawUseCase;

    @Autowired
    private GetCurrentBalanceUseCase getCurrentBalanceUseCase;

    @Test
    void concurrentWithdrawalsNeverOverdrawTheWallet() throws Exception {
        WalletView wallet = createWalletUseCase.execute(new CreateWalletCommand(new UserId(UUID.randomUUID())));
        depositUseCase.execute(new DepositCommand(wallet.walletId(), Money.of("100.00", SupportedCurrency.BRL), null));

        int attempts = 20;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < attempts; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    withdrawUseCase.execute(new WithdrawCommand(wallet.walletId(), Money.of("10.00", SupportedCurrency.BRL)));
                    succeeded.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    rejected.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        startLatch.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(succeeded.get()).isEqualTo(10);
        assertThat(rejected.get()).isEqualTo(10);

        BalanceView finalBalance = getCurrentBalanceUseCase.execute(wallet.walletId());
        assertThat(finalBalance.balance().amount()).isEqualByComparingTo("0.00");
    }
}
