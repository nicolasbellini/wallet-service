package com.recargapay.walletservice.application.dto;

import com.recargapay.walletservice.domain.model.UserId;

public record CreateWalletCommand(UserId userId) {
}
