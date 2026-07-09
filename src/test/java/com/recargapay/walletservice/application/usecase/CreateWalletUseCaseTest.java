package com.recargapay.walletservice.application.usecase;

import com.recargapay.walletservice.application.dto.CreateWalletCommand;
import com.recargapay.walletservice.application.dto.WalletView;
import com.recargapay.walletservice.domain.exception.DuplicateWalletException;
import com.recargapay.walletservice.domain.model.SupportedCurrency;
import com.recargapay.walletservice.domain.model.UserId;
import com.recargapay.walletservice.domain.model.Wallet;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateWalletUseCaseTest {

    @Mock
    private WalletRepository walletRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void createsWalletWithZeroBalanceForNewUser() {
        UserId userId = new UserId(UUID.randomUUID());
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateWalletUseCase useCase = new CreateWalletUseCase(walletRepository, clock);
        WalletView view = useCase.execute(new CreateWalletCommand(userId));

        assertThat(view.userId()).isEqualTo(userId);
        assertThat(view.balance().isZero()).isTrue();
        assertThat(view.balance().currency()).isEqualTo(SupportedCurrency.BRL);

        ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(userId);
    }

    @Test
    void rejectsDuplicateWalletForSameUser() {
        UserId userId = new UserId(UUID.randomUUID());
        Wallet existing = Wallet.createNew(com.recargapay.walletservice.domain.model.WalletId.generate(), userId, SupportedCurrency.BRL, Instant.now(clock));
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        CreateWalletUseCase useCase = new CreateWalletUseCase(walletRepository, clock);

        assertThatThrownBy(() -> useCase.execute(new CreateWalletCommand(userId)))
                .isInstanceOf(DuplicateWalletException.class);
        verify(walletRepository, never()).save(any());
    }
}
