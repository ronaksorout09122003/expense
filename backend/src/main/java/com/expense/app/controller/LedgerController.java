package com.expense.app.controller;

import com.expense.app.dto.LedgerDtos;
import com.expense.app.dto.TransactionDtos;
import com.expense.app.security.UserPrincipal;
import com.expense.app.service.LedgerService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;

    @GetMapping("/api/v1/dues/receivable")
    public List<LedgerDtos.LedgerSummaryResponse> receivable(@AuthenticationPrincipal UserPrincipal principal) {
        return ledgerService.receivable(principal.getUserId());
    }

    @GetMapping("/api/v1/dues/payable")
    public List<LedgerDtos.LedgerSummaryResponse> payable(@AuthenticationPrincipal UserPrincipal principal) {
        return ledgerService.payable(principal.getUserId());
    }

    @GetMapping("/api/v1/ledger/counterparty/{id}")
    public LedgerDtos.CounterpartyLedgerResponse ledger(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID id
    ) {
        return ledgerService.counterpartyLedger(principal.getUserId(), id);
    }

    @PostMapping("/api/v1/ledger/settlement")
    public TransactionDtos.TransactionDetailResponse createSettlement(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestBody LedgerDtos.SettlementRequest request
    ) {
        return ledgerService.createSettlement(principal.getUserId(), request);
    }
}
