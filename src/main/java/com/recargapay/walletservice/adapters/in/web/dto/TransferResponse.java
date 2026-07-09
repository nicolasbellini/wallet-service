package com.recargapay.walletservice.adapters.in.web.dto;

import com.recargapay.walletservice.application.dto.TransferResult;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferResponse(
        UUID transferId,
        UUID sourceWalletId,
        UUID destinationWalletId,
        BigDecimal amount,
        BigDecimal sourceBalanceAfter,
        BigDecimal destinationBalanceAfter
) {

    public static TransferResponse from(TransferResult result) {
        return new TransferResponse(
                result.transferId(),
                result.sourceWalletId().value(),
                result.destinationWalletId().value(),
                result.amount().amount(),
                result.sourceBalanceAfter().amount(),
                result.destinationBalanceAfter().amount());
    }
}
