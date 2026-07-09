package com.recargapay.walletservice.adapters.in.web;

import com.recargapay.walletservice.domain.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(WalletNotFoundException.class)
    public ProblemDetail handleWalletNotFound(WalletNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ProblemDetail handleInsufficientFunds(InsufficientFundsException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(DuplicateWalletException.class)
    public ProblemDetail handleDuplicateWallet(DuplicateWalletException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({SameWalletTransferException.class, InvalidAmountException.class, CurrencyMismatchException.class,
            IllegalArgumentException.class, MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail handleBadRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationFailure(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "The wallet was modified concurrently, please retry");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        String correlationId = UUID.randomUUID().toString();
        log.error("Unexpected error [correlationId={}]", correlationId, ex);
        ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setProperty("correlationId", correlationId);
        return problem;
    }

    private ProblemDetail problem(HttpStatus status, String message) {
        return ProblemDetail.forStatusAndDetail(status, message);
    }
}
