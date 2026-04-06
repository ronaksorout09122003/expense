package com.expense.app.controller;

import com.expense.app.dto.CommonDtos;
import com.expense.app.dto.TransactionDtos;
import com.expense.app.enums.TransactionType;
import com.expense.app.security.UserPrincipal;
import com.expense.app.service.TransactionService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    public CommonDtos.PagedResponse<TransactionDtos.TransactionSummaryResponse> list(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) TransactionType transactionType,
        @RequestParam(required = false) UUID accountId,
        @RequestParam(required = false) UUID categoryId,
        @RequestParam(required = false) UUID counterpartyId,
        @RequestParam(required = false) BigDecimal minAmount,
        @RequestParam(required = false) BigDecimal maxAmount,
        @RequestParam(required = false) String query,
        @RequestParam(defaultValue = "false") boolean dueOnly
    ) {
        return transactionService.list(
            principal.getUserId(),
            page,
            size,
            from,
            to,
            transactionType,
            accountId,
            categoryId,
            counterpartyId,
            minAmount,
            maxAmount,
            query,
            dueOnly
        );
    }

    @GetMapping("/{id}")
    public TransactionDtos.TransactionDetailResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return transactionService.get(principal.getUserId(), id);
    }

    @PostMapping
    public TransactionDtos.TransactionDetailResponse create(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody TransactionDtos.TransactionRequest request
    ) {
        return transactionService.create(principal.getUserId(), request);
    }

    @PutMapping("/{id}")
    public TransactionDtos.TransactionDetailResponse update(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID id,
        @Valid @RequestBody TransactionDtos.TransactionRequest request
    ) {
        return transactionService.update(principal.getUserId(), id, request);
    }

    @DeleteMapping("/{id}")
    public CommonDtos.MessageResponse delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        transactionService.delete(principal.getUserId(), id);
        return new CommonDtos.MessageResponse("Transaction voided successfully");
    }
}
