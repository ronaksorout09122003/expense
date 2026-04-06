package com.expense.app.service;

import com.expense.app.dto.CommonDtos;
import com.expense.app.dto.TransactionDtos;
import com.expense.app.entity.Account;
import com.expense.app.entity.AppUser;
import com.expense.app.entity.Category;
import com.expense.app.entity.Counterparty;
import com.expense.app.entity.Household;
import com.expense.app.entity.TransactionEntity;
import com.expense.app.entity.TxnSettlement;
import com.expense.app.enums.EntrySource;
import com.expense.app.enums.TransactionStatus;
import com.expense.app.enums.TransactionType;
import com.expense.app.exception.ApiException;
import com.expense.app.exception.NotFoundException;
import com.expense.app.repository.AccountRepository;
import com.expense.app.repository.AppUserRepository;
import com.expense.app.repository.CategoryRepository;
import com.expense.app.repository.CounterpartyRepository;
import com.expense.app.repository.HouseholdMemberRepository;
import com.expense.app.repository.HouseholdRepository;
import com.expense.app.repository.TransactionRepository;
import com.expense.app.repository.TxnSettlementRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TxnSettlementRepository txnSettlementRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final AppUserRepository appUserRepository;
    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final FinanceMathService financeMathService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public CommonDtos.PagedResponse<TransactionDtos.TransactionSummaryResponse> list(
        UUID userId,
        int page,
        int size,
        LocalDate from,
        LocalDate to,
        TransactionType transactionType,
        UUID accountId,
        UUID categoryId,
        UUID counterpartyId,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        String query,
        boolean dueOnly
    ) {
        List<TransactionEntity> transactions = findAllVisibleTransactions(userId);
        List<TxnSettlement> settlements = txnSettlementRepository.findByUserId(userId);
        Map<UUID, BigDecimal> outstanding = financeMathService.calculateOutstandingByBaseTxn(transactions, settlements);

        List<TransactionDtos.TransactionSummaryResponse> filtered = transactions.stream()
            .filter(transaction -> matches(transaction, outstanding, from, to, transactionType, accountId, categoryId, counterpartyId, minAmount, maxAmount, query, dueOnly))
            .sorted(Comparator.comparing(TransactionEntity::getTransactionAt).reversed())
            .map(transaction -> toSummary(transaction, outstanding))
            .toList();

        int normalizedSize = Math.max(size, 1);
        int fromIndex = Math.min(page * normalizedSize, filtered.size());
        int toIndex = Math.min(fromIndex + normalizedSize, filtered.size());

        return new CommonDtos.PagedResponse<>(
            filtered.subList(fromIndex, toIndex),
            page,
            normalizedSize,
            filtered.size(),
            (int) Math.ceil(filtered.size() / (double) normalizedSize)
        );
    }

    @Transactional(readOnly = true)
    public TransactionDtos.TransactionDetailResponse get(UUID userId, UUID id) {
        TransactionEntity transaction = getOwnedTransaction(userId, id);
        return toDetail(transaction, computeOutstanding(userId));
    }

    @Transactional(readOnly = true)
    public List<TransactionEntity> findEffectiveTransactions(UUID userId) {
        return findAllVisibleTransactions(userId).stream()
            .filter(financeMathService::isEffective)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionEntity> findAllVisibleTransactions(UUID userId) {
        return transactionRepository.findByUserIdAndDeletedAtIsNullOrderByTransactionAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<TxnSettlement> findSettlements(UUID userId) {
        return txnSettlementRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public TransactionEntity getOwnedTransaction(UUID userId, UUID id) {
        return transactionRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
            .orElseThrow(() -> new NotFoundException("Transaction not found"));
    }

    @Transactional
    public TransactionDtos.TransactionDetailResponse create(UUID userId, TransactionDtos.TransactionRequest request) {
        AppUser user = appUserRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        TransactionEntity transaction = new TransactionEntity();
        transaction.setUser(user);
        transaction.setEntrySource(EntrySource.WEB);
        TransactionEntity baseTxn = applyAndValidate(transaction, userId, request, null, null);
        transaction = transactionRepository.save(transaction);
        syncSettlement(user, transaction, baseTxn, request.baseTxnId());
        auditService.log(user, "txn", transaction.getId(), "CREATE", null, request);
        return get(userId, transaction.getId());
    }

    @Transactional
    public TransactionDtos.TransactionDetailResponse update(UUID userId, UUID id, TransactionDtos.TransactionRequest request) {
        TransactionEntity transaction = getOwnedTransaction(userId, id);
        TxnSettlement existingSettlement = txnSettlementRepository.findByUserIdAndSettlementTxnId(userId, id).stream().findFirst().orElse(null);
        boolean hasBaseSettlements = !txnSettlementRepository.findByUserIdAndBaseTxnId(userId, id).isEmpty();
        boolean linked = hasBaseSettlements || existingSettlement != null;

        if (linked) {
            validateLinkedUpdate(transaction, request, existingSettlement);
        }

        TransactionDtos.TransactionDetailResponse oldValue = get(userId, id);
        TransactionEntity baseTxn = applyAndValidate(transaction, userId, request, id, existingSettlement);
        transaction = transactionRepository.save(transaction);
        syncSettlement(transaction.getUser(), transaction, baseTxn, request.baseTxnId());
        auditService.log(transaction.getUser(), "txn", transaction.getId(), "UPDATE", oldValue, request);
        return get(userId, transaction.getId());
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        TransactionEntity transaction = getOwnedTransaction(userId, id);
        List<TxnSettlement> baseSettlements = txnSettlementRepository.findByUserIdAndBaseTxnId(userId, id);
        if (!baseSettlements.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot delete a transaction that already has repayments linked to it");
        }

        List<TxnSettlement> settlementLinks = txnSettlementRepository.findByUserIdAndSettlementTxnId(userId, id);
        if (!settlementLinks.isEmpty()) {
            txnSettlementRepository.deleteAll(settlementLinks);
        }

        transaction.setStatus(TransactionStatus.VOIDED);
        transaction.setDeletedAt(OffsetDateTime.now());
        transactionRepository.save(transaction);
        auditService.log(transaction.getUser(), "txn", id, "VOID", null, Map.of("status", "VOIDED"));
    }

    private TransactionEntity applyAndValidate(
        TransactionEntity transaction,
        UUID userId,
        TransactionDtos.TransactionRequest request,
        UUID existingTransactionId,
        TxnSettlement existingSettlement
    ) {
        Account fromAccount = findAccount(userId, request.fromAccountId());
        Account toAccount = findAccount(userId, request.toAccountId());
        Category category = findCategory(userId, request.categoryId());
        Counterparty counterparty = findCounterparty(userId, request.counterpartyId());

        if (request.amount() == null || request.amount().signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero");
        }
        if (request.transactionAt() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Transaction date and time are required");
        }

        switch (request.transactionType()) {
            case EXPENSE -> {
                require(fromAccount != null, "Expense requires a source account");
                require(category != null, "Expense requires a category");
                validateCategoryKind(category, true);
            }
            case INCOME -> {
                require(toAccount != null, "Income requires a destination account");
                if (category != null) {
                    validateIncomeCategory(category);
                }
            }
            case TRANSFER -> {
                require(fromAccount != null, "Transfer requires a source account");
                require(toAccount != null, "Transfer requires a destination account");
                require(!Objects.equals(fromAccount.getId(), toAccount.getId()), "Transfer between the same account is not allowed");
            }
            case LEND -> {
                require(fromAccount != null, "Lend requires a source account");
                validatePersonCounterparty(counterparty);
            }
            case BORROW -> {
                require(toAccount != null, "Borrow requires a destination account");
                validatePersonCounterparty(counterparty);
            }
            case REPAYMENT_IN -> {
                require(toAccount != null, "Repayment in requires a destination account");
            }
            case REPAYMENT_OUT -> {
                require(fromAccount != null, "Repayment out requires a source account");
            }
        }

        if (counterparty != null && request.transactionType().requiresCounterpartyPerson()) {
            validatePersonCounterparty(counterparty);
        }

        TransactionEntity baseTxn = null;
        if (request.transactionType().isSettlement()) {
            UUID baseTxnId = request.baseTxnId();
            if (baseTxnId == null && existingSettlement != null) {
                baseTxnId = existingSettlement.getBaseTxn().getId();
            }
            require(baseTxnId != null, "Repayment must be linked to a pending lend or borrow transaction");
            baseTxn = getOwnedTransaction(userId, baseTxnId);
            validateSettlement(transaction, request, counterparty, baseTxn, existingTransactionId);
            counterparty = baseTxn.getCounterparty();
        }

        transaction.setTransactionType(request.transactionType());
        transaction.setAmount(financeMathService.normalize(request.amount()));
        transaction.setTransactionAt(request.transactionAt());
        transaction.setFromAccount(fromAccount);
        transaction.setToAccount(toAccount);
        transaction.setCategory(category);
        transaction.setCounterparty(counterparty);
        transaction.setNote(blankToNull(request.note()));
        transaction.setDueDate(request.dueDate());
        transaction.setReferenceNo(blankToNull(request.referenceNo()));
        transaction.setLocationText(blankToNull(request.locationText()));
        transaction.setStatus(TransactionStatus.ACTIVE);
        Household household = findHousehold(userId, request.householdId(), request.shared());
        transaction.setShared(request.shared());
        transaction.setSharedParticipantCount(Math.max(request.sharedParticipantCount() == null ? 1 : request.sharedParticipantCount(), 1));
        transaction.setHousehold(household);
        return baseTxn;
    }

    private void validateSettlement(
        TransactionEntity transaction,
        TransactionDtos.TransactionRequest request,
        Counterparty requestedCounterparty,
        TransactionEntity baseTxn,
        UUID existingTransactionId
    ) {
        require(financeMathService.isEffective(baseTxn), "The selected base transaction is not active");
        if (request.transactionType() == TransactionType.REPAYMENT_IN) {
            require(baseTxn.getTransactionType() == TransactionType.LEND, "Repayment in must be linked to a lend transaction");
        }
        if (request.transactionType() == TransactionType.REPAYMENT_OUT) {
            require(baseTxn.getTransactionType() == TransactionType.BORROW, "Repayment out must be linked to a borrow transaction");
        }
        if (requestedCounterparty != null) {
            require(baseTxn.getCounterparty() != null && baseTxn.getCounterparty().getId().equals(requestedCounterparty.getId()),
                "Repayment counterparty must match the linked transaction");
        }

        BigDecimal pending = computeOutstandingForBase(baseTxn.getUser().getId(), baseTxn.getId());
        if (existingTransactionId != null) {
            TxnSettlement existingSettlement = txnSettlementRepository.findByBaseTxnIdAndSettlementTxnId(baseTxn.getId(), existingTransactionId).orElse(null);
            if (existingSettlement != null) {
                pending = pending.add(existingSettlement.getSettledAmount());
            }
        }
        require(request.amount().compareTo(pending) <= 0, "Settlement amount cannot exceed the pending amount");
    }

    private void validateLinkedUpdate(
        TransactionEntity existing,
        TransactionDtos.TransactionRequest request,
        TxnSettlement existingSettlement
    ) {
        require(existing.getTransactionType() == request.transactionType(), "Transaction type cannot be changed after settlement linkage");
        require(financeMathService.normalize(existing.getAmount()).compareTo(financeMathService.normalize(request.amount())) == 0,
            "Amount cannot be edited after settlement linkage");
        require(sameId(existing.getFromAccount(), request.fromAccountId()), "Source account cannot be changed after settlement linkage");
        require(sameId(existing.getToAccount(), request.toAccountId()), "Destination account cannot be changed after settlement linkage");
        require(sameId(existing.getCounterparty(), request.counterpartyId()), "Counterparty cannot be changed after settlement linkage");
        if (existingSettlement != null && request.baseTxnId() != null) {
            require(existingSettlement.getBaseTxn().getId().equals(request.baseTxnId()), "Linked base transaction cannot be changed");
        }
    }

    private boolean sameId(Object entity, UUID id) {
        if (entity == null && id == null) {
            return true;
        }
        if (entity instanceof Account account) {
            return account.getId().equals(id);
        }
        if (entity instanceof Counterparty counterparty) {
            return counterparty.getId().equals(id);
        }
        return false;
    }

    private void syncSettlement(AppUser user, TransactionEntity transaction, TransactionEntity baseTxn, UUID requestedBaseId) {
        if (!transaction.getTransactionType().isSettlement()) {
            return;
        }
        TxnSettlement settlement = txnSettlementRepository.findByUserIdAndSettlementTxnId(user.getId(), transaction.getId())
            .stream()
            .findFirst()
            .orElseGet(TxnSettlement::new);
        settlement.setUser(user);
        settlement.setBaseTxn(baseTxn != null ? baseTxn : getOwnedTransaction(user.getId(), requestedBaseId));
        settlement.setSettlementTxn(transaction);
        settlement.setSettledAmount(transaction.getAmount());
        txnSettlementRepository.save(settlement);
    }

    private Account findAccount(UUID userId, UUID id) {
        if (id == null) {
            return null;
        }
        Account account = accountRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
            .orElseThrow(() -> new NotFoundException("Account not found"));
        require(account.isActive(), "Inactive account cannot be used for a new transaction");
        return account;
    }

    private Category findCategory(UUID userId, UUID id) {
        if (id == null) {
            return null;
        }
        Category category = categoryRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
            .orElseThrow(() -> new NotFoundException("Category not found"));
        require(category.isActive(), "Inactive category cannot be used for a new transaction");
        return category;
    }

    private Counterparty findCounterparty(UUID userId, UUID id) {
        if (id == null) {
            return null;
        }
        Counterparty counterparty = counterpartyRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
            .orElseThrow(() -> new NotFoundException("Merchant or person not found"));
        require(counterparty.isActive(), "Inactive merchant or person cannot be used for a new transaction");
        return counterparty;
    }

    private Household findHousehold(UUID userId, UUID id, boolean shared) {
        if (!shared) {
            return null;
        }
        if (id == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Shared transactions must belong to a household");
        }
        Household household = householdRepository.findByIdAndActiveTrue(id)
            .orElseThrow(() -> new NotFoundException("Household not found"));
        require(householdMemberRepository.existsByHouseholdIdAndUserIdAndActiveTrue(household.getId(), userId),
            "You are not a member of this household");
        return household;
    }

    private void validatePersonCounterparty(Counterparty counterparty) {
        require(counterparty != null, "This transaction requires a person");
        require(counterparty.getCounterpartyType().supportsPersonLedger(), "This transaction requires a person counterparty");
    }

    private void validateCategoryKind(Category category, boolean expense) {
        if (expense) {
            require(category.getCategoryKind() != com.expense.app.enums.CategoryKind.INCOME, "Expense category must not be an income category");
        }
    }

    private void validateIncomeCategory(Category category) {
        require(category.getCategoryKind() != com.expense.app.enums.CategoryKind.EXPENSE, "Income category must not be an expense category");
    }

    private CommonDtos.PagedResponse<TransactionDtos.TransactionSummaryResponse> pageOf(List<TransactionDtos.TransactionSummaryResponse> filtered, int page, int size) {
        return null;
    }

    private boolean matches(
        TransactionEntity transaction,
        Map<UUID, BigDecimal> outstanding,
        LocalDate from,
        LocalDate to,
        TransactionType transactionType,
        UUID accountId,
        UUID categoryId,
        UUID counterpartyId,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        String query,
        boolean dueOnly
    ) {
        LocalDate txnDate = transaction.getTransactionAt().toLocalDate();
        if (from != null && txnDate.isBefore(from)) {
            return false;
        }
        if (to != null && txnDate.isAfter(to)) {
            return false;
        }
        if (transactionType != null && transaction.getTransactionType() != transactionType) {
            return false;
        }
        if (accountId != null && !(matchesId(transaction.getFromAccount(), accountId) || matchesId(transaction.getToAccount(), accountId))) {
            return false;
        }
        if (categoryId != null && !matchesCategory(transaction.getCategory(), categoryId)) {
            return false;
        }
        if (counterpartyId != null && !matchesId(transaction.getCounterparty(), counterpartyId)) {
            return false;
        }
        if (minAmount != null && transaction.getAmount().compareTo(minAmount) < 0) {
            return false;
        }
        if (maxAmount != null && transaction.getAmount().compareTo(maxAmount) > 0) {
            return false;
        }
        if (query != null && !query.isBlank()) {
            String text = query.trim().toLowerCase();
            String haystack = Stream.of(
                    transaction.getNote(),
                    transaction.getReferenceNo(),
                    transaction.getLocationText(),
                    transaction.getCounterparty() != null ? transaction.getCounterparty().getName() : null,
                    transaction.getCategory() != null ? transaction.getCategory().getName() : null
                )
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .reduce("", (left, right) -> left + " " + right);
            if (!haystack.contains(text)) {
                return false;
            }
        }
        if (dueOnly) {
            BigDecimal outstandingAmount = outstanding.getOrDefault(transaction.getId(), BigDecimal.ZERO);
            return (transaction.getTransactionType() == TransactionType.LEND || transaction.getTransactionType() == TransactionType.BORROW)
                && transaction.getDueDate() != null
                && outstandingAmount.signum() > 0;
        }
        return true;
    }

    private boolean matchesId(Account account, UUID id) {
        return account != null && account.getId().equals(id);
    }

    private boolean matchesId(Counterparty counterparty, UUID id) {
        return counterparty != null && counterparty.getId().equals(id);
    }

    private boolean matchesCategory(Category category, UUID id) {
        return category != null && (category.getId().equals(id) || (category.getParentCategory() != null && category.getParentCategory().getId().equals(id)));
    }

    private Map<UUID, BigDecimal> computeOutstanding(UUID userId) {
        List<TransactionEntity> transactions = findAllVisibleTransactions(userId);
        List<TxnSettlement> settlements = txnSettlementRepository.findByUserId(userId);
        return financeMathService.calculateOutstandingByBaseTxn(transactions, settlements);
    }

    private BigDecimal computeOutstandingForBase(UUID userId, UUID baseTxnId) {
        return computeOutstanding(userId).getOrDefault(baseTxnId, BigDecimal.ZERO);
    }

    private TransactionDtos.TransactionSummaryResponse toSummary(TransactionEntity transaction, Map<UUID, BigDecimal> outstanding) {
        return new TransactionDtos.TransactionSummaryResponse(
            transaction.getId(),
            transaction.getTransactionType(),
            financeMathService.normalize(transaction.getAmount()),
            transaction.getTransactionAt(),
            transaction.getNote(),
            transaction.getStatus(),
            transaction.getFromAccount() != null ? transaction.getFromAccount().getId() : null,
            transaction.getFromAccount() != null ? transaction.getFromAccount().getName() : null,
            transaction.getToAccount() != null ? transaction.getToAccount().getId() : null,
            transaction.getToAccount() != null ? transaction.getToAccount().getName() : null,
            transaction.getCategory() != null ? transaction.getCategory().getId() : null,
            transaction.getCategory() != null ? transaction.getCategory().getName() : null,
            buildCategoryPath(transaction.getCategory()),
            transaction.getCounterparty() != null ? transaction.getCounterparty().getId() : null,
            transaction.getCounterparty() != null ? transaction.getCounterparty().getName() : null,
            transaction.getDueDate(),
            outstanding.getOrDefault(transaction.getId(), BigDecimal.ZERO),
            transaction.isShared(),
            transaction.getSharedParticipantCount(),
            transaction.getHousehold() != null ? transaction.getHousehold().getId() : null,
            transaction.getHousehold() != null ? transaction.getHousehold().getName() : null
        );
    }

    private TransactionDtos.TransactionDetailResponse toDetail(TransactionEntity transaction, Map<UUID, BigDecimal> outstanding) {
        List<TransactionDtos.SettlementInfoResponse> settlements = txnSettlementRepository.findByUserIdAndBaseTxnId(transaction.getUser().getId(), transaction.getId())
            .stream()
            .sorted(Comparator.comparing(settlement -> settlement.getSettlementTxn().getTransactionAt()))
            .map(settlement -> new TransactionDtos.SettlementInfoResponse(
                settlement.getId(),
                settlement.getSettlementTxn().getId(),
                settlement.getSettlementTxn().getTransactionAt(),
                financeMathService.normalize(settlement.getSettledAmount()),
                settlement.getSettlementTxn().getNote()
            ))
            .toList();

        return new TransactionDtos.TransactionDetailResponse(
            transaction.getId(),
            transaction.getTransactionType(),
            financeMathService.normalize(transaction.getAmount()),
            transaction.getTransactionAt(),
            transaction.getFromAccount() != null ? transaction.getFromAccount().getId() : null,
            transaction.getFromAccount() != null ? transaction.getFromAccount().getName() : null,
            transaction.getToAccount() != null ? transaction.getToAccount().getId() : null,
            transaction.getToAccount() != null ? transaction.getToAccount().getName() : null,
            transaction.getCategory() != null ? transaction.getCategory().getId() : null,
            transaction.getCategory() != null ? transaction.getCategory().getName() : null,
            buildCategoryPath(transaction.getCategory()),
            transaction.getCounterparty() != null ? transaction.getCounterparty().getId() : null,
            transaction.getCounterparty() != null ? transaction.getCounterparty().getName() : null,
            transaction.getNote(),
            transaction.getReferenceNo(),
            transaction.getLocationText(),
            transaction.getDueDate(),
            transaction.getStatus(),
            outstanding.getOrDefault(transaction.getId(), BigDecimal.ZERO),
            transaction.isShared(),
            transaction.getSharedParticipantCount(),
            transaction.getHousehold() != null ? transaction.getHousehold().getId() : null,
            transaction.getHousehold() != null ? transaction.getHousehold().getName() : null,
            settlements
        );
    }

    private String buildCategoryPath(Category category) {
        if (category == null) {
            return null;
        }
        if (category.getParentCategory() == null) {
            return category.getName();
        }
        return category.getParentCategory().getName() + " / " + category.getName();
    }

    private void require(boolean valid, String message) {
        if (!valid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
