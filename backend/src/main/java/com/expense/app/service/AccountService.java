package com.expense.app.service;

import com.expense.app.dto.AccountDtos;
import com.expense.app.entity.Account;
import com.expense.app.entity.AppUser;
import com.expense.app.exception.ApiException;
import com.expense.app.exception.NotFoundException;
import com.expense.app.repository.AccountRepository;
import com.expense.app.repository.AppUserRepository;
import com.expense.app.repository.TransactionRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AppUserRepository appUserRepository;
    private final FinanceMathService financeMathService;
    private final AuditService auditService;

    public List<AccountDtos.AccountResponse> list(UUID userId) {
        List<Account> accounts = accountRepository.findByUserIdAndDeletedAtIsNullOrderByNameAsc(userId);
        Map<UUID, java.math.BigDecimal> balances = financeMathService.calculateAccountBalances(
            accounts,
            transactionRepository.findByUserIdAndDeletedAtIsNullOrderByTransactionAtDesc(userId)
        );
        return accounts.stream().map(account -> toResponse(account, balances)).toList();
    }

    public AccountDtos.AccountResponse get(UUID userId, UUID id) {
        return list(userId).stream()
            .filter(account -> account.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Account not found"));
    }

    @Transactional
    public AccountDtos.AccountResponse create(UUID userId, AccountDtos.AccountRequest request) {
        AppUser user = appUserRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        ensureUniqueName(userId, request.name(), null);

        Account account = new Account();
        account.setUser(user);
        apply(account, request, true);
        account = accountRepository.save(account);
        auditService.log(user, "account", account.getId(), "CREATE", null, request);
        return get(userId, account.getId());
    }

    @Transactional
    public AccountDtos.AccountResponse update(UUID userId, UUID id, AccountDtos.AccountRequest request) {
        Account account = accountRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
            .orElseThrow(() -> new NotFoundException("Account not found"));
        ensureUniqueName(userId, request.name(), id);
        AccountDtos.AccountResponse oldValue = get(userId, id);
        apply(account, request, account.isActive());
        account = accountRepository.save(account);
        auditService.log(account.getUser(), "account", account.getId(), "UPDATE", oldValue, request);
        return get(userId, account.getId());
    }

    @Transactional
    public void deactivate(UUID userId, UUID id) {
        Account account = accountRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
            .orElseThrow(() -> new NotFoundException("Account not found"));
        account.setActive(false);
        accountRepository.save(account);
        auditService.log(account.getUser(), "account", id, "DEACTIVATE", null, Map.of("active", false));
    }

    private void apply(Account account, AccountDtos.AccountRequest request, boolean defaultActive) {
        account.setName(request.name().trim());
        account.setAccountType(request.accountType());
        account.setOpeningBalance(financeMathService.normalize(request.openingBalance()));
        account.setAccentColor(request.accentColor());
        account.setActive(request.active() != null ? request.active() : defaultActive);
    }

    private void ensureUniqueName(UUID userId, String name, UUID existingId) {
        String normalized = name.trim();
        boolean duplicate = accountRepository.findByUserIdAndDeletedAtIsNullOrderByNameAsc(userId).stream()
            .anyMatch(account -> account.getName().trim().equalsIgnoreCase(normalized)
                && (existingId == null || !account.getId().equals(existingId)));
        if (duplicate) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Account name already exists");
        }
    }

    private AccountDtos.AccountResponse toResponse(Account account, Map<UUID, java.math.BigDecimal> balances) {
        return new AccountDtos.AccountResponse(
            account.getId(),
            account.getName(),
            account.getAccountType(),
            financeMathService.normalize(account.getOpeningBalance()),
            financeMathService.normalize(balances.getOrDefault(account.getId(), account.getOpeningBalance())),
            account.getAccentColor(),
            account.isActive()
        );
    }
}
