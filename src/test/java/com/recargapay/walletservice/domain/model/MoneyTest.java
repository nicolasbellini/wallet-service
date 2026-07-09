package com.recargapay.walletservice.domain.model;

import com.recargapay.walletservice.domain.exception.CurrencyMismatchException;
import com.recargapay.walletservice.domain.exception.InvalidAmountException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    private static final Currency BRL = Currency.getInstance("BRL");

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> new Money(new BigDecimal("-1.00"), BRL))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void roundsToTwoDecimalPlaces() {
        Money money = new Money(new BigDecimal("10.005"), BRL);
        assertThat(money.amount()).isEqualByComparingTo("10.00");
    }

    @Test
    void addsAmountsOfSameCurrency() {
        Money a = Money.of("10.00", BRL);
        Money b = Money.of("5.50", BRL);
        assertThat(a.add(b).amount()).isEqualByComparingTo("15.50");
    }

    @Test
    void subtractsAmountsOfSameCurrency() {
        Money a = Money.of("10.00", BRL);
        Money b = Money.of("5.50", BRL);
        assertThat(a.subtract(b).amount()).isEqualByComparingTo("4.50");
    }

    @Test
    void rejectsCurrencyMismatchOnAdd() {
        Money brl = Money.of("10.00", BRL);
        Money usd = Money.of("10.00", Currency.getInstance("USD"));
        assertThatThrownBy(() -> brl.add(usd)).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void subtractBelowZeroThrows() {
        Money a = Money.of("5.00", BRL);
        Money b = Money.of("10.00", BRL);
        assertThatThrownBy(() -> a.subtract(b)).isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void isLessThanComparesCorrectly() {
        assertThat(Money.of("5.00", BRL).isLessThan(Money.of("10.00", BRL))).isTrue();
        assertThat(Money.of("10.00", BRL).isLessThan(Money.of("5.00", BRL))).isFalse();
    }

    @Test
    void zeroIsNeitherPositiveNorLessThanItself() {
        Money zero = Money.zero(BRL);
        assertThat(zero.isZero()).isTrue();
        assertThat(zero.isPositive()).isFalse();
    }
}
