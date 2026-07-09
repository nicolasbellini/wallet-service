package com.recargapay.walletservice.adapters.in.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DepositRequest(
        @NotNull @DecimalMin(value = "0.01", message = "amount must be positive") BigDecimal amount,
        String reference
) {
}
