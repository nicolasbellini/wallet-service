package com.recargapay.walletservice.adapters.in.web;

import com.recargapay.walletservice.adapters.in.web.dto.TransferRequest;
import com.recargapay.walletservice.adapters.in.web.dto.TransferResponse;
import com.recargapay.walletservice.application.dto.TransferCommand;
import com.recargapay.walletservice.application.dto.TransferResult;
import com.recargapay.walletservice.application.usecase.TransferFundsUseCase;
import com.recargapay.walletservice.domain.model.Money;
import com.recargapay.walletservice.domain.model.SupportedCurrency;
import com.recargapay.walletservice.domain.model.WalletId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfer")
public class TransferController {

    private final TransferFundsUseCase transferFundsUseCase;

    public TransferController(TransferFundsUseCase transferFundsUseCase) {
        this.transferFundsUseCase = transferFundsUseCase;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request) {
        TransferCommand command = new TransferCommand(
                new WalletId(request.sourceWalletId()),
                new WalletId(request.destinationWalletId()),
                new Money(request.amount(), SupportedCurrency.BRL));
        TransferResult result = transferFundsUseCase.execute(command);
        return ResponseEntity.status(201).body(TransferResponse.from(result));
    }
}
