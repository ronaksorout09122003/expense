package com.expense.app.controller;

import com.expense.app.dto.AccountDtos;
import com.expense.app.dto.CommonDtos;
import com.expense.app.security.UserPrincipal;
import com.expense.app.service.AccountService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public List<AccountDtos.AccountResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return accountService.list(principal.getUserId());
    }

    @GetMapping("/{id}")
    public AccountDtos.AccountResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return accountService.get(principal.getUserId(), id);
    }

    @PostMapping
    public AccountDtos.AccountResponse create(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody AccountDtos.AccountRequest request
    ) {
        return accountService.create(principal.getUserId(), request);
    }

    @PutMapping("/{id}")
    public AccountDtos.AccountResponse update(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID id,
        @Valid @RequestBody AccountDtos.AccountRequest request
    ) {
        return accountService.update(principal.getUserId(), id, request);
    }

    @DeleteMapping("/{id}")
    public CommonDtos.MessageResponse deactivate(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        accountService.deactivate(principal.getUserId(), id);
        return new CommonDtos.MessageResponse("Account deactivated");
    }
}
