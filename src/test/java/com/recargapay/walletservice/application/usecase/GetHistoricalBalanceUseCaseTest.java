package com.recargapay.walletservice.application.usecase;

import com.recargapay.walletservice.application.dto.BalanceView;
import com.recargapay.walletservice.domain.model.*;
import com.recargapay.walletservice.domain.port.LedgerRepository;
import com.recargapay.walletservice.domain.port.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetHistoricalBalanceUseCaseTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private LedgerRepository ledgerRepository;

    @Test
    void returnsBalanceAfterOfLatestEntryAtOrBeforeAsOf() {
        WalletId walletId = WalletId.generate();
        Instant asOf = Instant.parse("2026-01-15T00:00:00Z");
        Wallet wallet = new Wallet(walletId, new UserId(UUID.randomUUID()), Money.of("999.00", SupportedCurrency.BRL), Instant.parse("2026-01-01T00:00:00Z"));
        LedgerEntry entry = LedgerEntry.deposit(walletId, Money.of("100.00", SupportedCurrency.BRL), Money.of("100.00", SupportedCurrency.BRL), Instant.parse("2026-01-10T00:00:00Z"), null);

        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        when(ledgerRepository.findLatestAsOf(walletId, asOf)).thenReturn(Optional.of(entry));

        GetHistoricalBalanceUseCase useCase = new GetHistoricalBalanceUseCase(walletRepository, ledgerRepository);
        BalanceView view = useCase.execute(walletId, asOf);

        assertThat(view.balance().amount()).isEqualByComparingTo("100.00");
        assertThat(view.asOf()).isEqualTo(asOf);
    }

    @Test
    void returnsZeroWhenNoEntryExistsAtOrBeforeAsOf() {
        WalletId walletId = WalletId.generate();
        Instant asOf = Instant.parse("2026-01-01T00:00:00Z");
        Wallet wallet = new Wallet(walletId, new UserId(UUID.randomUUID()), Money.of("999.00", SupportedCurrency.BRL), Instant.parse("2026-02-01T00:00:00Z"));

        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        when(ledgerRepository.findLatestAsOf(walletId, asOf)).thenReturn(Optional.empty());

        GetHistoricalBalanceUseCase useCase = new GetHistoricalBalanceUseCase(walletRepository, ledgerRepository);
        BalanceView view = useCase.execute(walletId, asOf);

        assertThat(view.balance().isZero()).isTrue();
    }
}
