package com.recargapay.walletservice.adapters.in.web.dto;

import com.recargapay.walletservice.application.dto.DepositResult;

import java.math.BigDecimal;
import java.util.UUID;

public record DepositResponse(UUID walletId, UUID entryId, BigDecimal newBalance) {

    public static DepositResponse from(DepositResult result) {
        return new DepositResponse(result.walletId().value(), result.entryId(), result.newBalance().amount());
    }
}
