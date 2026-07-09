package com.recargapay.walletservice.application.usecase;

import com.recargapay.walletservice.application.dto.DepositCommand;
import com.recargapay.walletservice.application.dto.DepositResult;
import com.recargapay.walletservice.domain.exception.WalletNotFoundException;
import com.recargapay.walletservice.domain.model.*;
import com.recargapay.walletservice.domain.port.LedgerRepository;
import com.recargapay.walletservice.domain.port.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepositUseCaseTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private LedgerRepository ledgerRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void increasesBalanceAndAppendsLedgerEntry() {
        WalletId walletId = WalletId.generate();
        Wallet wallet = new Wallet(walletId, new UserId(UUID.randomUUID()), Money.of("50.00", SupportedCurrency.BRL), Instant.now(clock));
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerRepository.append(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DepositUseCase useCase = new DepositUseCase(walletRepository, ledgerRepository, clock);
        DepositResult result = useCase.execute(new DepositCommand(walletId, Money.of("25.00", SupportedCurrency.BRL), "salary"));

        assertThat(result.newBalance().amount()).isEqualByComparingTo("75.00");

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository).append(captor.capture());
        assertThat(captor.getValue().entryType()).isEqualTo(EntryType.DEPOSIT);
        assertThat(captor.getValue().balanceAfter().amount()).isEqualByComparingTo("75.00");
    }

    @Test
    void throwsWhenWalletMissing() {
        WalletId walletId = WalletId.generate();
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.empty());

        DepositUseCase useCase = new DepositUseCase(walletRepository, ledgerRepository, clock);

        assertThatThrownBy(() -> useCase.execute(new DepositCommand(walletId, Money.of("25.00", SupportedCurrency.BRL), null)))
                .isInstanceOf(WalletNotFoundException.class);
    }
}
