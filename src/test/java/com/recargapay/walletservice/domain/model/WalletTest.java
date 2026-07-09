package com.recargapay.walletservice.domain.model;

import com.recargapay.walletservice.domain.exception.InsufficientFundsException;
import com.recargapay.walletservice.domain.exception.InvalidAmountException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletTest {

    private static final Currency BRL = Currency.getInstance("BRL");

    private Wallet newWallet() {
        return Wallet.createNew(WalletId.generate(), new UserId(java.util.UUID.randomUUID()), BRL, Instant.now());
    }

    @Test
    void newWalletStartsAtZeroBalance() {
        Wallet wallet = newWallet();
        assertThat(wallet.balance().isZero()).isTrue();
    }

    @Test
    void depositIncreasesBalanceAndReturnsNewInstance() {
        Wallet wallet = newWallet();
        Wallet afterDeposit = wallet.deposit(Money.of("100.00", BRL));

        assertThat(afterDeposit.balance().amount()).isEqualByComparingTo("100.00");
        assertThat(wallet.balance().isZero()).isTrue(); // original untouched
    }

    @Test
    void withdrawDecreasesBalance() {
        Wallet wallet = newWallet().deposit(Money.of("100.00", BRL));
        Wallet afterWithdraw = wallet.withdraw(Money.of("40.00", BRL));

        assertThat(afterWithdraw.balance().amount()).isEqualByComparingTo("60.00");
    }

    @Test
    void withdrawBeyondBalanceThrowsInsufficientFunds() {
        Wallet wallet = newWallet().deposit(Money.of("10.00", BRL));

        assertThatThrownBy(() -> wallet.withdraw(Money.of("10.01", BRL)))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void depositOfZeroThrowsInvalidAmount() {
        Wallet wallet = newWallet();
        assertThatThrownBy(() -> wallet.deposit(Money.zero(BRL)))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void withdrawOfZeroThrowsInvalidAmount() {
        Wallet wallet = newWallet().deposit(Money.of("10.00", BRL));
        assertThatThrownBy(() -> wallet.withdraw(Money.zero(BRL)))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void equalityIsBasedOnIdOnly() {
        WalletId id = WalletId.generate();
        UserId userId = new UserId(java.util.UUID.randomUUID());
        Instant createdAt = Instant.now();

        Wallet a = new Wallet(id, userId, Money.zero(BRL), createdAt);
        Wallet b = new Wallet(id, userId, Money.of("50.00", BRL), createdAt);

        assertThat(a).isEqualTo(b);
    }
}
