package com.recargapay.walletservice.domain.model;

import java.util.Currency;

/**
 * The single currency supported application-wide (documented assumption —
 * see TRADEOFFS.md). Kept as one named constant rather than hardcoding
 * "BRL" string literals across layers.
 */
public final class SupportedCurrency {

    public static final Currency BRL = Currency.getInstance("BRL");

    private SupportedCurrency() {
    }
}
