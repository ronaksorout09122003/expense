package com.expense.app.service;

import com.expense.app.dto.ReportDtos;
import com.expense.app.dto.TransactionDtos;
import com.expense.app.entity.Account;
import com.expense.app.entity.Category;
import com.expense.app.entity.TransactionEntity;
import com.expense.app.enums.AccountType;
import com.expense.app.enums.TransactionType;
import com.expense.app.repository.AccountRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final TransactionService transactionService;
    private final AccountRepository accountRepository;
    private final FinanceMathService financeMathService;

    @Transactional(readOnly = true)
    public ReportDtos.DashboardResponse dashboard(UUID userId) {
        List<TransactionEntity> transactions = transactionService.findEffectiveTransactions(userId);
        Map<UUID, BigDecimal> outstanding = financeMathService.calculateOutstandingByBaseTxn(
            transactionService.findAllVisibleTransactions(userId),
            transactionService.findSettlements(userId)
        );
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate monthStart = today.withDayOfMonth(1);

        BigDecimal todayExpense = expenseForDate(transactions, today);
        BigDecimal yesterdayExpense = expenseForDate(transactions, yesterday);
        BigDecimal currentMonthExpense = expenseBetween(transactions, monthStart, today);

        List<Account> accounts = accountRepository.findByUserIdAndDeletedAtIsNullOrderByNameAsc(userId);
        Map<UUID, BigDecimal> balances = financeMathService.calculateAccountBalances(accounts, transactionService.findAllVisibleTransactions(userId));
        BigDecimal cashBalance = accounts.stream()
            .filter(account -> account.getAccountType() == AccountType.CASH)
            .map(account -> balances.getOrDefault(account.getId(), account.getOpeningBalance()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal nonCashBalance = accounts.stream()
            .filter(account -> account.getAccountType() != AccountType.CASH)
            .map(account -> balances.getOrDefault(account.getId(), account.getOpeningBalance()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal receivableOutstanding = transactions.stream()
            .filter(transaction -> transaction.getTransactionType() == TransactionType.LEND)
            .map(transaction -> outstanding.getOrDefault(transaction.getId(), BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal payableOutstanding = transactions.stream()
            .filter(transaction -> transaction.getTransactionType() == TransactionType.BORROW)
            .map(transaction -> outstanding.getOrDefault(transaction.getId(), BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<TransactionDtos.TransactionSummaryResponse> recentTransactions = transactionService.list(
            userId,
            0,
            8,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false
        ).content();

        List<ReportDtos.SummaryPointResponse> topCategories = categorySummary(userId, monthStart, today).stream().limit(5).toList();

        long overdueCount = transactions.stream()
            .filter(transaction -> transaction.getTransactionType() == TransactionType.LEND || transaction.getTransactionType() == TransactionType.BORROW)
            .filter(transaction -> financeMathService.isOverdue(transaction, outstanding.getOrDefault(transaction.getId(), BigDecimal.ZERO)))
            .count();

        return new ReportDtos.DashboardResponse(
            financeMathService.normalize(todayExpense),
            financeMathService.normalize(yesterdayExpense),
            financeMathService.normalize(currentMonthExpense),
            financeMathService.normalize(cashBalance),
            financeMathService.normalize(nonCashBalance),
            financeMathService.normalize(receivableOutstanding),
            financeMathService.normalize(payableOutstanding),
            recentTransactions,
            topCategories,
            overdueCount
        );
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.SummaryPointResponse> categorySummary(UUID userId, LocalDate from, LocalDate to) {
        return groupByLabel(filterRange(transactionService.findEffectiveTransactions(userId), from, to).stream()
            .filter(transaction -> transaction.getTransactionType() == TransactionType.EXPENSE)
            .filter(transaction -> transaction.getCategory() != null)
            .map(transaction -> Map.entry(rootCategoryName(transaction.getCategory()), transaction.getAmount())));
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.SummaryPointResponse> subcategorySummary(UUID userId, LocalDate from, LocalDate to, UUID parentCategoryId) {
        return groupByLabel(filterRange(transactionService.findEffectiveTransactions(userId), from, to).stream()
            .filter(transaction -> transaction.getTransactionType() == TransactionType.EXPENSE)
            .filter(transaction -> transaction.getCategory() != null)
            .filter(transaction -> parentCategoryId == null || categoryMatchesParent(transaction.getCategory(), parentCategoryId))
            .map(transaction -> Map.entry(subcategoryLabel(transaction.getCategory()), transaction.getAmount())));
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.SummaryPointResponse> accountSummary(UUID userId, LocalDate from, LocalDate to) {
        Map<String, BigDecimal> grouped = new LinkedHashMap<>();
        for (TransactionEntity transaction : filterRange(transactionService.findEffectiveTransactions(userId), from, to)) {
            BigDecimal amount = transaction.getAmount();
            switch (transaction.getTransactionType()) {
                case EXPENSE, LEND, REPAYMENT_OUT -> add(grouped, transaction.getFromAccount() != null ? transaction.getFromAccount().getName() : "Unknown", amount.negate());
                case INCOME, BORROW, REPAYMENT_IN -> add(grouped, transaction.getToAccount() != null ? transaction.getToAccount().getName() : "Unknown", amount);
                case TRANSFER -> {
                    add(grouped, transaction.getFromAccount() != null ? transaction.getFromAccount().getName() : "Unknown", amount.negate());
                    add(grouped, transaction.getToAccount() != null ? transaction.getToAccount().getName() : "Unknown", amount);
                }
            }
        }
        return grouped.entrySet().stream()
            .map(entry -> new ReportDtos.SummaryPointResponse(entry.getKey(), financeMathService.normalize(entry.getValue()), 0))
            .sorted(Comparator.comparing(ReportDtos.SummaryPointResponse::amount).reversed())
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.SummaryPointResponse> counterpartySummary(UUID userId, LocalDate from, LocalDate to) {
        return groupByLabel(filterRange(transactionService.findEffectiveTransactions(userId), from, to).stream()
            .filter(transaction -> transaction.getCounterparty() != null)
            .filter(transaction -> transaction.getTransactionType() != TransactionType.TRANSFER)
            .map(transaction -> Map.entry(transaction.getCounterparty().getName(), transaction.getAmount())));
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.TimelinePointResponse> cashFlow(UUID userId, LocalDate from, LocalDate to) {
        Map<LocalDate, BigDecimal> incoming = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> outgoing = new LinkedHashMap<>();

        for (TransactionEntity transaction : filterRange(transactionService.findEffectiveTransactions(userId), from, to)) {
            LocalDate date = transaction.getTransactionAt().toLocalDate();
            switch (transaction.getTransactionType()) {
                case INCOME, BORROW, REPAYMENT_IN -> add(incoming, date, transaction.getAmount());
                case EXPENSE, LEND, REPAYMENT_OUT -> add(outgoing, date, transaction.getAmount());
                case TRANSFER -> {
                }
            }
        }

        return java.util.stream.Stream.concat(incoming.keySet().stream(), outgoing.keySet().stream())
            .distinct()
            .sorted()
            .map(date -> {
                BigDecimal in = incoming.getOrDefault(date, BigDecimal.ZERO);
                BigDecimal out = outgoing.getOrDefault(date, BigDecimal.ZERO);
                return new ReportDtos.TimelinePointResponse(date, financeMathService.normalize(in), financeMathService.normalize(out), financeMathService.normalize(in.subtract(out)));
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public ReportDtos.PeriodComparisonResponse periodComparison(UUID userId, LocalDate from, LocalDate to) {
        LocalDate normalizedTo = to != null ? to : LocalDate.now();
        LocalDate normalizedFrom = from != null ? from : normalizedTo.withDayOfMonth(1);
        long periodDays = Math.max(ChronoUnit.DAYS.between(normalizedFrom, normalizedTo) + 1, 1);
        LocalDate previousTo = normalizedFrom.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(periodDays - 1);

        BigDecimal currentExpense = expenseBetween(transactionService.findEffectiveTransactions(userId), normalizedFrom, normalizedTo);
        BigDecimal previousExpense = expenseBetween(transactionService.findEffectiveTransactions(userId), previousFrom, previousTo);
        BigDecimal delta = financeMathService.normalize(currentExpense.subtract(previousExpense));
        double deltaPercent = previousExpense.signum() == 0
            ? (currentExpense.signum() == 0 ? 0 : 100)
            : delta.multiply(BigDecimal.valueOf(100)).divide(previousExpense, 2, java.math.RoundingMode.HALF_UP).doubleValue();
        return new ReportDtos.PeriodComparisonResponse(
            financeMathService.normalize(currentExpense),
            financeMathService.normalize(previousExpense),
            delta,
            deltaPercent
        );
    }

    @Transactional(readOnly = true)
    public ReportDtos.ForecastResponse forecast(UUID userId, LocalDate from, LocalDate to) {
        LocalDate normalizedTo = to != null ? to : LocalDate.now();
        LocalDate normalizedFrom = from != null ? from : normalizedTo.withDayOfMonth(1);
        List<TransactionEntity> rangeTransactions = filterRange(transactionService.findEffectiveTransactions(userId), normalizedFrom, normalizedTo);
        long elapsedDays = Math.max(ChronoUnit.DAYS.between(normalizedFrom, normalizedTo) + 1, 1);
        long daysInMonth = normalizedTo.lengthOfMonth();
        BigDecimal expense = rangeTransactions.stream()
            .filter(transaction -> transaction.getTransactionType() == TransactionType.EXPENSE)
            .map(TransactionEntity::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal income = rangeTransactions.stream()
            .filter(transaction -> transaction.getTransactionType() == TransactionType.INCOME)
            .map(TransactionEntity::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgExpense = financeMathService.normalize(expense.divide(BigDecimal.valueOf(elapsedDays), 2, java.math.RoundingMode.HALF_UP));
        BigDecimal avgIncome = financeMathService.normalize(income.divide(BigDecimal.valueOf(elapsedDays), 2, java.math.RoundingMode.HALF_UP));
        BigDecimal projectedExpense = financeMathService.normalize(avgExpense.multiply(BigDecimal.valueOf(daysInMonth)));
        BigDecimal projectedIncome = financeMathService.normalize(avgIncome.multiply(BigDecimal.valueOf(daysInMonth)));
        return new ReportDtos.ForecastResponse(
            avgExpense,
            avgIncome,
            projectedExpense,
            projectedIncome,
            financeMathService.normalize(projectedIncome.subtract(projectedExpense))
        );
    }

    private List<TransactionEntity> filterRange(List<TransactionEntity> transactions, LocalDate from, LocalDate to) {
        return transactions.stream()
            .filter(transaction -> from == null || !transaction.getTransactionAt().toLocalDate().isBefore(from))
            .filter(transaction -> to == null || !transaction.getTransactionAt().toLocalDate().isAfter(to))
            .toList();
    }

    private List<ReportDtos.SummaryPointResponse> groupByLabel(java.util.stream.Stream<Map.Entry<String, BigDecimal>> stream) {
        Map<String, BigDecimal> grouped = new LinkedHashMap<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        stream.forEach(entry -> {
            add(grouped, entry.getKey(), entry.getValue());
            counts.merge(entry.getKey(), 1L, Long::sum);
        });
        return grouped.entrySet().stream()
            .map(entry -> new ReportDtos.SummaryPointResponse(entry.getKey(), financeMathService.normalize(entry.getValue()), counts.getOrDefault(entry.getKey(), 0L)))
            .sorted(Comparator.comparing(ReportDtos.SummaryPointResponse::amount).reversed())
            .toList();
    }

    private String rootCategoryName(Category category) {
        return category.getParentCategory() == null ? category.getName() : category.getParentCategory().getName();
    }

    private String subcategoryLabel(Category category) {
        return category.getParentCategory() == null ? category.getName() : category.getParentCategory().getName() + " / " + category.getName();
    }

    private boolean categoryMatchesParent(Category category, UUID parentCategoryId) {
        return category.getId().equals(parentCategoryId)
            || (category.getParentCategory() != null && category.getParentCategory().getId().equals(parentCategoryId));
    }

    private BigDecimal expenseForDate(List<TransactionEntity> transactions, LocalDate date) {
        return transactions.stream()
            .filter(transaction -> transaction.getTransactionType() == TransactionType.EXPENSE)
            .filter(transaction -> transaction.getTransactionAt().toLocalDate().isEqual(date))
            .map(TransactionEntity::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal expenseBetween(List<TransactionEntity> transactions, LocalDate from, LocalDate to) {
        return filterRange(transactions, from, to).stream()
            .filter(transaction -> transaction.getTransactionType() == TransactionType.EXPENSE)
            .map(TransactionEntity::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void add(Map<String, BigDecimal> map, String key, BigDecimal amount) {
        map.merge(key, amount, BigDecimal::add);
    }

    private void add(Map<LocalDate, BigDecimal> map, LocalDate key, BigDecimal amount) {
        map.merge(key, amount, BigDecimal::add);
    }
}
