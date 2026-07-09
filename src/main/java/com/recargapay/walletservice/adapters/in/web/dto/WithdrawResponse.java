package com.recargapay.walletservice.adapters.in.web.dto;

import com.recargapay.walletservice.application.dto.WithdrawResult;

import java.math.BigDecimal;
import java.util.UUID;

public record WithdrawResponse(UUID walletId, UUID entryId, BigDecimal newBalance) {

    public static WithdrawResponse from(WithdrawResult result) {
        return new WithdrawResponse(result.walletId().value(), result.entryId(), result.newBalance().amount());
    }
}
