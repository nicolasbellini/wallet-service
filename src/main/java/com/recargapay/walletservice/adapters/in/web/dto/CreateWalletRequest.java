package com.recargapay.walletservice.adapters.in.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateWalletRequest(@NotNull UUID userId) {
}
