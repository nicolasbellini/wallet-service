package com.recargapay.walletservice.adapters.in.web;

import com.recargapay.walletservice.adapters.in.web.dto.*;
import com.recargapay.walletservice.application.dto.*;
import com.recargapay.walletservice.application.idempotency.IdempotencyService;
import com.recargapay.walletservice.application.usecase.*;
import com.recargapay.walletservice.domain.model.Money;
import com.recargapay.walletservice.domain.model.SupportedCurrency;
import com.recargapay.walletservice.domain.model.UserId;
import com.recargapay.walletservice.domain.model.WalletId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/wallet")
public class WalletController {

    private final CreateWalletUseCase createWalletUseCase;
    private final GetCurrentBalanceUseCase getCurrentBalanceUseCase;
    private final GetHistoricalBalanceUseCase getHistoricalBalanceUseCase;
    private final DepositUseCase depositUseCase;
    private final WithdrawUseCase withdrawUseCase;
    private final IdempotencyService idempotencyService;

    public WalletController(CreateWalletUseCase createWalletUseCase,
                             GetCurrentBalanceUseCase getCurrentBalanceUseCase,
                             GetHistoricalBalanceUseCase getHistoricalBalanceUseCase,
                             DepositUseCase depositUseCase,
                             WithdrawUseCase withdrawUseCase,
                             IdempotencyService idempotencyService) {
        this.createWalletUseCase = createWalletUseCase;
        this.getCurrentBalanceUseCase = getCurrentBalanceUseCase;
        this.getHistoricalBalanceUseCase = getHistoricalBalanceUseCase;
        this.depositUseCase = depositUseCase;
        this.withdrawUseCase = withdrawUseCase;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    public ResponseEntity<WalletResponse> createWallet(
            @Valid @RequestBody CreateWalletRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        return idempotencyService.executeIdempotent(idempotencyKey, httpRequest.getRequestURI(), WalletResponse.class, () -> {
            WalletView view = createWalletUseCase.execute(new CreateWalletCommand(new UserId(request.userId())));
            WalletResponse response = WalletResponse.from(view);
            return ResponseEntity.created(URI.create("/api/v1/wallet/" + response.walletId())).body(response);
        });
    }

    @GetMapping("/{walletId}/balance")
    public BalanceResponse currentBalance(@PathVariable String walletId) {
        BalanceView view = getCurrentBalanceUseCase.execute(WalletId.of(walletId));
        return BalanceResponse.from(view);
    }

    @GetMapping("/{walletId}/balance/history")
    public BalanceResponse historicalBalance(@PathVariable String walletId, @RequestParam Instant asOf) {
        BalanceView view = getHistoricalBalanceUseCase.execute(WalletId.of(walletId), asOf);
        return BalanceResponse.from(view);
    }

    @PostMapping("/{walletId}/deposit")
    public ResponseEntity<DepositResponse> deposit(
            @PathVariable String walletId,
            @Valid @RequestBody DepositRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        return idempotencyService.executeIdempotent(idempotencyKey, httpRequest.getRequestURI(), DepositResponse.class, () -> {
            DepositCommand command = new DepositCommand(
                    WalletId.of(walletId), new Money(request.amount(), SupportedCurrency.BRL), request.reference());
            DepositResult result = depositUseCase.execute(command);
            return ResponseEntity.status(201).body(DepositResponse.from(result));
        });
    }

    @PostMapping("/{walletId}/withdrawal")
    public ResponseEntity<WithdrawResponse> withdraw(
            @PathVariable String walletId,
            @Valid @RequestBody WithdrawRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        return idempotencyService.executeIdempotent(idempotencyKey, httpRequest.getRequestURI(), WithdrawResponse.class, () -> {
            WithdrawCommand command = new WithdrawCommand(WalletId.of(walletId), new Money(request.amount(), SupportedCurrency.BRL));
            WithdrawResult result = withdrawUseCase.execute(command);
            return ResponseEntity.ok(WithdrawResponse.from(result));
        });
    }
}
