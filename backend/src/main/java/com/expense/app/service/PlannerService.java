package com.expense.app.service;

import com.expense.app.dto.PlannerDtos;
import com.expense.app.dto.TransactionDtos;
import com.expense.app.entity.Account;
import com.expense.app.entity.AppUser;
import com.expense.app.entity.Budget;
import com.expense.app.entity.Category;
import com.expense.app.entity.Counterparty;
import com.expense.app.entity.Household;
import com.expense.app.entity.RecurringRule;
import com.expense.app.entity.SavingsGoal;
import com.expense.app.entity.TransactionEntity;
import com.expense.app.enums.RecurringFrequency;
import com.expense.app.enums.TransactionType;
import com.expense.app.exception.ApiException;
import com.expense.app.exception.NotFoundException;
import com.expense.app.repository.AccountRepository;
import com.expense.app.repository.AppUserRepository;
import com.expense.app.repository.BudgetRepository;
import com.expense.app.repository.CategoryRepository;
import com.expense.app.repository.CounterpartyRepository;
import com.expense.app.repository.HouseholdMemberRepository;
import com.expense.app.repository.HouseholdRepository;
import com.expense.app.repository.RecurringRuleRepository;
import com.expense.app.repository.SavingsGoalRepository;
import com.expense.app.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlannerService {

    private final RecurringRuleRepository recurringRuleRepository;
    private final BudgetRepository budgetRepository;
    private final SavingsGoalRepository savingsGoalRepository;
    private final AppUserRepository appUserRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final FinanceMathService financeMathService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<PlannerDtos.RecurringRuleResponse> recurringRules(UUID userId) {
        return recurringRuleRepository.findByUserIdOrderByNextRunAtAsc(userId).stream()
            .map(this::toRecurringResponse)
            .toList();
    }

    @Transactional
    public PlannerDtos.RecurringRuleResponse saveRecurringRule(UUID userId, PlannerDtos.RecurringRuleRequest request) {
        AppUser user = appUserRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        RecurringRule rule = request.id() == null
            ? new RecurringRule()
            : recurringRuleRepository.findByIdAndUserId(request.id(), userId).orElseThrow(() -> new NotFoundException("Recurring rule not found"));
        boolean creating = rule.getId() == null;
        rule.setUser(user);
        rule.setTitle(request.title().trim());
        rule.setTransactionType(request.transactionType());
        rule.setAmount(financeMathService.normalize(request.amount()));
        rule.setAccount(findAccount(userId, request.accountId()));
        rule.setToAccount(findAccount(userId, request.toAccountId()));
        rule.setCategory(findCategory(userId, request.categoryId()));
        rule.setCounterparty(findCounterparty(userId, request.counterpartyId()));
        rule.setFrequencyType(request.frequencyType());
        rule.setNextRunAt(request.nextRunAt());
        rule.setIntervalDays(Math.max(request.intervalDays(), 1));
        rule.setDueDateOffsetDays(Math.max(request.dueDateOffsetDays(), 0));
        rule.setRemindDaysBefore(Math.max(request.remindDaysBefore(), 0));
        rule.setNote(blankToNull(request.note()));
        rule.setReferenceNo(blankToNull(request.referenceNo()));
        rule.setLocationText(blankToNull(request.locationText()));
        rule.setAutoCreate(request.autoCreate());
        rule.setActive(request.active());
        rule.setShared(request.shared());
        rule.setSharedParticipantCount(Math.max(request.sharedParticipantCount() == null ? 1 : request.sharedParticipantCount(), 1));
        rule.setHousehold(findHousehold(userId, request.householdId(), request.shared()));
        validateRecurringRule(rule);
        RecurringRule saved = recurringRuleRepository.save(rule);
        auditService.log(user, "recurring_rule", saved.getId(), creating ? "CREATE" : "UPDATE", null, request);
        return toRecurringResponse(saved);
    }

    @Transactional
    public TransactionDtos.TransactionDetailResponse runRecurringRule(UUID userId, UUID id) {
        RecurringRule rule = recurringRuleRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new NotFoundException("Recurring rule not found"));
        require(rule.isActive(), "Inactive recurring rules cannot create transactions");

        OffsetDateTime effectiveTime = rule.getNextRunAt() != null ? rule.getNextRunAt() : OffsetDateTime.now();
        TransactionDtos.TransactionRequest request = new TransactionDtos.TransactionRequest(
            rule.getTransactionType(),
            rule.getAmount(),
            effectiveTime,
            usesFromAccount(rule.getTransactionType()) ? rule.getAccount() != null ? rule.getAccount().getId() : null : null,
            usesToAccount(rule.getTransactionType()) ? resolveToAccount(rule) : null,
            rule.getCategory() != null ? rule.getCategory().getId() : null,
            rule.getCounterparty() != null ? rule.getCounterparty().getId() : null,
            rule.getNote(),
            rule.getDueDateOffsetDays() > 0 ? effectiveTime.toLocalDate().plusDays(rule.getDueDateOffsetDays()) : null,
            rule.getReferenceNo(),
            rule.getLocationText(),
            null,
            rule.isShared(),
            rule.getSharedParticipantCount(),
            rule.getHousehold() != null ? rule.getHousehold().getId() : null
        );

        TransactionDtos.TransactionDetailResponse detail = transactionService.create(userId, request);
        TransactionEntity transaction = transactionRepository.findById(detail.id()).orElseThrow(() -> new NotFoundException("Transaction not found"));
        transaction.setRecurringRule(rule);
        transactionRepository.save(transaction);

        rule.setLastRunAt(OffsetDateTime.now());
        rule.setNextRunAt(computeNextRun(rule.getFrequencyType(), effectiveTime, rule.getIntervalDays()));
        recurringRuleRepository.save(rule);
        auditService.log(rule.getUser(), "recurring_rule", rule.getId(), "RUN", null, detail);
        return detail;
    }

    @Transactional(readOnly = true)
    public List<PlannerDtos.CalendarItemResponse> calendar(UUID userId) {
        List<PlannerDtos.CalendarItemResponse> items = new ArrayList<>();
        recurringRuleRepository.findByUserIdAndActiveTrueOrderByNextRunAtAsc(userId).stream()
            .limit(10)
            .forEach(rule -> items.add(new PlannerDtos.CalendarItemResponse(
                "RECURRING",
                rule.getTitle(),
                financeMathService.normalize(rule.getAmount()),
                rule.getNextRunAt() != null ? rule.getNextRunAt().toLocalDate() : LocalDate.now(),
                "Next " + rule.getFrequencyType().name().toLowerCase().replace('_', ' ')
            )));
        transactionService.findAllVisibleTransactions(userId).stream()
            .filter(transaction -> transaction.getDueDate() != null)
            .filter(transaction -> financeMathService.isEffective(transaction))
            .filter(transaction -> !transaction.getDueDate().isBefore(LocalDate.now().minusDays(2)))
            .limit(10)
            .forEach(transaction -> items.add(new PlannerDtos.CalendarItemResponse(
                "DUE",
                transaction.getCounterparty() != null ? transaction.getCounterparty().getName() : transaction.getTransactionType().name(),
                financeMathService.normalize(transaction.getAmount()),
                transaction.getDueDate(),
                transaction.getTransactionType().name()
            )));
        savingsGoalRepository.findByUserIdAndActiveTrueOrderByCreatedAtDesc(userId).stream()
            .filter(goal -> goal.getTargetDate() != null)
            .limit(10)
            .forEach(goal -> items.add(new PlannerDtos.CalendarItemResponse(
                "GOAL",
                goal.getTitle(),
                financeMathService.normalize(goal.getTargetAmount().subtract(goal.getSavedAmount())),
                goal.getTargetDate(),
                "Remaining to goal"
            )));
        return items.stream()
            .sorted(Comparator.comparing(PlannerDtos.CalendarItemResponse::dueDate))
            .limit(18)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<PlannerDtos.BudgetResponse> budgets(UUID userId) {
        List<TransactionEntity> effectiveTransactions = transactionService.findEffectiveTransactions(userId);
        return budgetRepository.findByUserIdOrderByPeriodStartDesc(userId).stream()
            .map(budget -> toBudgetResponse(budget, effectiveTransactions))
            .toList();
    }

    @Transactional
    public PlannerDtos.BudgetResponse saveBudget(UUID userId, PlannerDtos.BudgetRequest request) {
        AppUser user = appUserRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        Budget budget = request.id() == null
            ? new Budget()
            : budgetRepository.findByIdAndUserId(request.id(), userId).orElseThrow(() -> new NotFoundException("Budget not found"));
        budget.setUser(user);
        budget.setTitle(request.title().trim());
        budget.setCategory(findCategory(userId, request.categoryId()));
        budget.setAmountLimit(financeMathService.normalize(request.amountLimit()));
        budget.setPeriodStart(request.periodStart());
        budget.setPeriodEnd(request.periodEnd());
        budget.setAlertPercent(Math.max(request.alertPercent(), 1));
        budget.setRolloverEnabled(request.rolloverEnabled());
        budget.setActive(request.active());
        require(!budget.getPeriodEnd().isBefore(budget.getPeriodStart()), "Budget end date must be on or after the start date");
        Budget saved = budgetRepository.save(budget);
        auditService.log(user, "budget", saved.getId(), request.id() == null ? "CREATE" : "UPDATE", null, request);
        return toBudgetResponse(saved, transactionService.findEffectiveTransactions(userId));
    }

    @Transactional(readOnly = true)
    public List<PlannerDtos.SavingsGoalResponse> goals(UUID userId) {
        return savingsGoalRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(this::toGoalResponse)
            .toList();
    }

    @Transactional
    public PlannerDtos.SavingsGoalResponse saveGoal(UUID userId, PlannerDtos.SavingsGoalRequest request) {
        AppUser user = appUserRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        SavingsGoal goal = request.id() == null
            ? new SavingsGoal()
            : savingsGoalRepository.findByIdAndUserId(request.id(), userId).orElseThrow(() -> new NotFoundException("Savings goal not found"));
        goal.setUser(user);
        goal.setTitle(request.title().trim());
        goal.setTargetAmount(financeMathService.normalize(request.targetAmount()));
        goal.setSavedAmount(financeMathService.normalize(request.savedAmount()));
        goal.setTargetDate(request.targetDate());
        goal.setAccount(findAccount(userId, request.accountId()));
        goal.setNotes(blankToNull(request.notes()));
        goal.setActive(request.active());
        require(goal.getSavedAmount().compareTo(goal.getTargetAmount()) <= 0, "Saved amount cannot exceed target amount");
        SavingsGoal saved = savingsGoalRepository.save(goal);
        auditService.log(user, "savings_goal", saved.getId(), request.id() == null ? "CREATE" : "UPDATE", null, request);
        return toGoalResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PlannerDtos.ReminderResponse> reminders(UUID userId) {
        List<PlannerDtos.ReminderResponse> reminders = new ArrayList<>();
        List<TransactionEntity> effectiveTransactions = transactionService.findEffectiveTransactions(userId);

        effectiveTransactions.stream()
            .filter(transaction -> transaction.getDueDate() != null)
            .filter(transaction -> transaction.getTransactionType() == TransactionType.LEND || transaction.getTransactionType() == TransactionType.BORROW)
            .forEach(transaction -> {
                LocalDate dueDate = transaction.getDueDate();
                String counterpartyName = transaction.getCounterparty() != null ? transaction.getCounterparty().getName() : transaction.getTransactionType().name();
                if (dueDate.isBefore(LocalDate.now())) {
                    reminders.add(new PlannerDtos.ReminderResponse(
                        "high",
                        "Overdue follow-up",
                        counterpartyName + " has an overdue due item to close",
                        dueDate,
                        financeMathService.normalize(transaction.getAmount())
                    ));
                } else if (!dueDate.isAfter(LocalDate.now().plusDays(3))) {
                    reminders.add(new PlannerDtos.ReminderResponse(
                        "medium",
                        "Upcoming due date",
                        counterpartyName + " is due soon",
                        dueDate,
                        financeMathService.normalize(transaction.getAmount())
                    ));
                }
            });

        budgetRepository.findByUserIdAndActiveTrueOrderByPeriodStartDesc(userId).forEach(budget -> {
            PlannerDtos.BudgetResponse response = toBudgetResponse(budget, effectiveTransactions);
            if (response.usagePercent() >= budget.getAlertPercent()) {
                reminders.add(new PlannerDtos.ReminderResponse(
                    response.usagePercent() >= 100 ? "high" : "medium",
                    "Budget watch",
                    response.title() + " is at " + Math.round(response.usagePercent()) + "% of its limit",
                    response.periodEnd(),
                    response.remainingAmount()
                ));
            }
        });

        recurringRuleRepository.findByUserIdAndActiveTrueOrderByNextRunAtAsc(userId).forEach(rule -> {
            LocalDate nextDate = rule.getNextRunAt() != null ? rule.getNextRunAt().toLocalDate() : LocalDate.now();
            if (!nextDate.isAfter(LocalDate.now().plusDays(Math.max(rule.getRemindDaysBefore(), 3)))) {
                reminders.add(new PlannerDtos.ReminderResponse(
                    "low",
                    "Recurring item due",
                    rule.getTitle() + " is coming up next",
                    nextDate,
                    financeMathService.normalize(rule.getAmount())
                ));
            }
        });

        savingsGoalRepository.findByUserIdAndActiveTrueOrderByCreatedAtDesc(userId).forEach(goal -> {
            if (goal.getTargetDate() != null && goal.getTargetAmount().compareTo(goal.getSavedAmount()) > 0 && !goal.getTargetDate().isAfter(LocalDate.now().plusDays(14))) {
                reminders.add(new PlannerDtos.ReminderResponse(
                    "medium",
                    "Goal target approaching",
                    goal.getTitle() + " needs more funding",
                    goal.getTargetDate(),
                    financeMathService.normalize(goal.getTargetAmount().subtract(goal.getSavedAmount()))
                ));
            }
        });

        return reminders.stream()
            .sorted(Comparator.comparing(PlannerDtos.ReminderResponse::dueDate, Comparator.nullsLast(Comparator.naturalOrder())))
            .limit(12)
            .toList();
    }

    private PlannerDtos.RecurringRuleResponse toRecurringResponse(RecurringRule rule) {
        return new PlannerDtos.RecurringRuleResponse(
            rule.getId(),
            rule.getTitle(),
            rule.getTransactionType(),
            financeMathService.normalize(rule.getAmount()),
            rule.getAccount() != null ? rule.getAccount().getId() : null,
            rule.getAccount() != null ? rule.getAccount().getName() : null,
            rule.getToAccount() != null ? rule.getToAccount().getId() : null,
            rule.getToAccount() != null ? rule.getToAccount().getName() : null,
            rule.getCategory() != null ? rule.getCategory().getId() : null,
            rule.getCategory() != null ? rule.getCategory().getName() : null,
            rule.getCounterparty() != null ? rule.getCounterparty().getId() : null,
            rule.getCounterparty() != null ? rule.getCounterparty().getName() : null,
            rule.getFrequencyType(),
            rule.getNextRunAt(),
            rule.getIntervalDays(),
            rule.getDueDateOffsetDays(),
            rule.getRemindDaysBefore(),
            rule.getNote(),
            rule.getReferenceNo(),
            rule.getLocationText(),
            rule.isAutoCreate(),
            rule.isActive(),
            rule.isShared(),
            rule.getSharedParticipantCount(),
            rule.getHousehold() != null ? rule.getHousehold().getId() : null,
            rule.getHousehold() != null ? rule.getHousehold().getName() : null,
            rule.getLastRunAt()
        );
    }

    private PlannerDtos.BudgetResponse toBudgetResponse(Budget budget, List<TransactionEntity> effectiveTransactions) {
        BigDecimal spent = effectiveTransactions.stream()
            .filter(transaction -> transaction.getTransactionType() == TransactionType.EXPENSE)
            .filter(transaction -> !transaction.getTransactionAt().toLocalDate().isBefore(budget.getPeriodStart()))
            .filter(transaction -> !transaction.getTransactionAt().toLocalDate().isAfter(budget.getPeriodEnd()))
            .filter(transaction -> matchesBudgetCategory(transaction.getCategory(), budget.getCategory()))
            .map(TransactionEntity::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal normalizedSpent = financeMathService.normalize(spent);
        BigDecimal limit = financeMathService.normalize(budget.getAmountLimit());
        BigDecimal remaining = financeMathService.normalize(limit.subtract(normalizedSpent));
        double usagePercent = limit.signum() == 0 ? 0 : normalizedSpent.multiply(BigDecimal.valueOf(100)).divide(limit, 2, RoundingMode.HALF_UP).doubleValue();
        return new PlannerDtos.BudgetResponse(
            budget.getId(),
            budget.getTitle(),
            budget.getCategory() != null ? budget.getCategory().getId() : null,
            budget.getCategory() != null ? budget.getCategory().getName() : "All expenses",
            limit,
            budget.getPeriodStart(),
            budget.getPeriodEnd(),
            budget.getAlertPercent(),
            budget.isRolloverEnabled(),
            budget.isActive(),
            normalizedSpent,
            remaining,
            usagePercent
        );
    }

    private PlannerDtos.SavingsGoalResponse toGoalResponse(SavingsGoal goal) {
        BigDecimal target = financeMathService.normalize(goal.getTargetAmount());
        BigDecimal saved = financeMathService.normalize(goal.getSavedAmount());
        double progress = target.signum() == 0 ? 0 : saved.multiply(BigDecimal.valueOf(100)).divide(target, 2, RoundingMode.HALF_UP).doubleValue();
        return new PlannerDtos.SavingsGoalResponse(
            goal.getId(),
            goal.getTitle(),
            target,
            saved,
            goal.getTargetDate(),
            goal.getAccount() != null ? goal.getAccount().getId() : null,
            goal.getAccount() != null ? goal.getAccount().getName() : null,
            goal.getNotes(),
            goal.isActive(),
            progress
        );
    }

    private boolean matchesBudgetCategory(Category transactionCategory, Category budgetCategory) {
        if (budgetCategory == null) {
            return true;
        }
        if (transactionCategory == null) {
            return false;
        }
        return transactionCategory.getId().equals(budgetCategory.getId())
            || (transactionCategory.getParentCategory() != null && transactionCategory.getParentCategory().getId().equals(budgetCategory.getId()));
    }

    private void validateRecurringRule(RecurringRule rule) {
        require(rule.getAmount() != null && rule.getAmount().signum() > 0, "Recurring amount must be greater than zero");
        require(rule.getNextRunAt() != null, "Recurring rules need a next run date");
        switch (rule.getTransactionType()) {
            case EXPENSE, LEND, REPAYMENT_OUT -> require(rule.getAccount() != null, "This recurring type needs a source account");
            case INCOME, BORROW, REPAYMENT_IN -> require(rule.getAccount() != null, "This recurring type needs an account");
            case TRANSFER -> {
                require(rule.getAccount() != null, "Transfer rules need a source account");
                require(rule.getToAccount() != null, "Transfer rules need a destination account");
                require(!rule.getAccount().getId().equals(rule.getToAccount().getId()), "Transfer accounts must be different");
            }
        }
        if (rule.getTransactionType() == TransactionType.EXPENSE) {
            require(rule.getCategory() != null, "Expense rules need a category");
        }
        if (rule.getTransactionType().requiresCounterpartyPerson()) {
            require(rule.getCounterparty() != null, "This recurring type needs a person");
        }
        if (rule.isShared()) {
            require(rule.getHousehold() != null, "Shared recurring rules need a household");
        }
    }

    private OffsetDateTime computeNextRun(RecurringFrequency frequency, OffsetDateTime previous, int intervalDays) {
        return switch (frequency) {
            case DAILY -> previous.plusDays(1);
            case WEEKLY -> previous.plusWeeks(1);
            case MONTHLY -> previous.plusMonths(1);
            case CUSTOM_DAYS -> previous.plusDays(Math.max(intervalDays, 1));
        };
    }

    private UUID resolveToAccount(RecurringRule rule) {
        if (rule.getTransactionType() == TransactionType.TRANSFER) {
            return rule.getToAccount() != null ? rule.getToAccount().getId() : null;
        }
        return rule.getAccount() != null ? rule.getAccount().getId() : null;
    }

    private boolean usesFromAccount(TransactionType transactionType) {
        return transactionType == TransactionType.EXPENSE
            || transactionType == TransactionType.TRANSFER
            || transactionType == TransactionType.LEND
            || transactionType == TransactionType.REPAYMENT_OUT;
    }

    private boolean usesToAccount(TransactionType transactionType) {
        return transactionType == TransactionType.INCOME
            || transactionType == TransactionType.TRANSFER
            || transactionType == TransactionType.BORROW
            || transactionType == TransactionType.REPAYMENT_IN;
    }

    private Account findAccount(UUID userId, UUID id) {
        if (id == null) {
            return null;
        }
        return accountRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
            .orElseThrow(() -> new NotFoundException("Account not found"));
    }

    private Category findCategory(UUID userId, UUID id) {
        if (id == null) {
            return null;
        }
        return categoryRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
            .orElseThrow(() -> new NotFoundException("Category not found"));
    }

    private Counterparty findCounterparty(UUID userId, UUID id) {
        if (id == null) {
            return null;
        }
        return counterpartyRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
            .orElseThrow(() -> new NotFoundException("Counterparty not found"));
    }

    private Household findHousehold(UUID userId, UUID id, boolean shared) {
        if (!shared) {
            return null;
        }
        if (id == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Shared items need a household");
        }
        Household household = householdRepository.findByIdAndActiveTrue(id)
            .orElseThrow(() -> new NotFoundException("Household not found"));
        require(householdMemberRepository.existsByHouseholdIdAndUserIdAndActiveTrue(id, userId), "You are not a member of this household");
        return household;
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
