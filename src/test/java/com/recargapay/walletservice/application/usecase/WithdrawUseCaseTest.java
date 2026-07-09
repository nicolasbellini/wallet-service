package com.recargapay.walletservice.application.usecase;

import com.recargapay.walletservice.application.dto.WithdrawCommand;
import com.recargapay.walletservice.application.dto.WithdrawResult;
import com.recargapay.walletservice.domain.exception.InsufficientFundsException;
import com.recargapay.walletservice.domain.model.*;
import com.recargapay.walletservice.domain.port.LedgerRepository;
import com.recargapay.walletservice.domain.port.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WithdrawUseCaseTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private LedgerRepository ledgerRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void decreasesBalanceAndAppendsLedgerEntry() {
        WalletId walletId = WalletId.generate();
        Wallet wallet = new Wallet(walletId, new UserId(UUID.randomUUID()), Money.of("50.00", SupportedCurrency.BRL), Instant.now(clock));
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerRepository.append(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WithdrawUseCase useCase = new WithdrawUseCase(walletRepository, ledgerRepository, clock);
        WithdrawResult result = useCase.execute(new WithdrawCommand(walletId, Money.of("20.00", SupportedCurrency.BRL)));

        assertThat(result.newBalance().amount()).isEqualByComparingTo("30.00");
    }

    @Test
    void insufficientFundsPreventsWithdrawalAndLedgerAppend() {
        WalletId walletId = WalletId.generate();
        Wallet wallet = new Wallet(walletId, new UserId(UUID.randomUUID()), Money.of("10.00", SupportedCurrency.BRL), Instant.now(clock));
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));

        WithdrawUseCase useCase = new WithdrawUseCase(walletRepository, ledgerRepository, clock);

        assertThatThrownBy(() -> useCase.execute(new WithdrawCommand(walletId, Money.of("10.01", SupportedCurrency.BRL))))
                .isInstanceOf(InsufficientFundsException.class);

        verify(walletRepository, never()).save(any());
        verify(ledgerRepository, never()).append(any());
    }
}
