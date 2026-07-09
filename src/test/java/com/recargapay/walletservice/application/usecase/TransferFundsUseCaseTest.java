package com.recargapay.walletservice.application.usecase;

import com.recargapay.walletservice.application.dto.TransferCommand;
import com.recargapay.walletservice.application.dto.TransferResult;
import com.recargapay.walletservice.domain.exception.SameWalletTransferException;
import com.recargapay.walletservice.domain.model.*;
import com.recargapay.walletservice.domain.port.LedgerRepository;
import com.recargapay.walletservice.domain.port.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferFundsUseCaseTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private LedgerRepository ledgerRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void rejectsTransferToSameWallet() {
        WalletId walletId = WalletId.generate();
        TransferFundsUseCase useCase = new TransferFundsUseCase(walletRepository, ledgerRepository, clock);

        assertThatThrownBy(() -> useCase.execute(new TransferCommand(walletId, walletId, Money.of("10.00", SupportedCurrency.BRL))))
                .isInstanceOf(SameWalletTransferException.class);
        verifyNoInteractions(walletRepository, ledgerRepository);
    }

    @Test
    void locksWalletsInAscendingIdOrderAndLinksLedgerEntriesByTransferId() {
        // Two arbitrary wallet ids; sort them to know the expected lock order up front.
        WalletId walletA = WalletId.generate();
        WalletId walletB = WalletId.generate();
        List<WalletId> sorted = walletA.value().compareTo(walletB.value()) <= 0
                ? List.of(walletA, walletB)
                : List.of(walletB, walletA);
        WalletId expectedFirstLock = sorted.get(0);
        WalletId expectedSecondLock = sorted.get(1);

        Wallet sourceWallet = new Wallet(walletA, new UserId(UUID.randomUUID()), Money.of("100.00", SupportedCurrency.BRL), Instant.now(clock));
        Wallet destinationWallet = new Wallet(walletB, new UserId(UUID.randomUUID()), Money.of("10.00", SupportedCurrency.BRL), Instant.now(clock));

        when(walletRepository.findByIdForUpdate(walletA)).thenReturn(Optional.of(sourceWallet));
        when(walletRepository.findByIdForUpdate(walletB)).thenReturn(Optional.of(destinationWallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerRepository.append(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransferFundsUseCase useCase = new TransferFundsUseCase(walletRepository, ledgerRepository, clock);
        TransferResult result = useCase.execute(new TransferCommand(walletA, walletB, Money.of("30.00", SupportedCurrency.BRL)));

        InOrder inOrder = inOrder(walletRepository);
        inOrder.verify(walletRepository).findByIdForUpdate(expectedFirstLock);
        inOrder.verify(walletRepository).findByIdForUpdate(expectedSecondLock);

        assertThat(result.sourceBalanceAfter().amount()).isEqualByComparingTo("70.00");
        assertThat(result.destinationBalanceAfter().amount()).isEqualByComparingTo("40.00");

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository, times(2)).append(captor.capture());
        List<LedgerEntry> entries = captor.getAllValues();
        assertThat(entries.get(0).transferId()).isEqualTo(entries.get(1).transferId());
        assertThat(entries).extracting(LedgerEntry::entryType)
                .containsExactlyInAnyOrder(EntryType.TRANSFER_OUT, EntryType.TRANSFER_IN);
    }
}
