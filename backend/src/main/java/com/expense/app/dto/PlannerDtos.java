package com.expense.app.dto;

import com.expense.app.enums.RecurringFrequency;
import com.expense.app.enums.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class PlannerDtos {

    private PlannerDtos() {
    }

    public record RecurringRuleRequest(
        UUID id,
        @NotBlank @Size(max = 140) String title,
        @NotNull TransactionType transactionType,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        UUID accountId,
        UUID toAccountId,
        UUID categoryId,
        UUID counterpartyId,
        @NotNull RecurringFrequency frequencyType,
        @NotNull OffsetDateTime nextRunAt,
        int intervalDays,
        int dueDateOffsetDays,
        int remindDaysBefore,
        @Size(max = 500) String note,
        @Size(max = 100) String referenceNo,
        @Size(max = 180) String locationText,
        boolean autoCreate,
        boolean active,
        boolean shared,
        Integer sharedParticipantCount,
        UUID householdId
    ) {
    }

    public record RecurringRuleResponse(
        UUID id,
        String title,
        TransactionType transactionType,
        BigDecimal amount,
        UUID accountId,
        String accountName,
        UUID toAccountId,
        String toAccountName,
        UUID categoryId,
        String categoryName,
        UUID counterpartyId,
        String counterpartyName,
        RecurringFrequency frequencyType,
        OffsetDateTime nextRunAt,
        Integer intervalDays,
        Integer dueDateOffsetDays,
        Integer remindDaysBefore,
        String note,
        String referenceNo,
        String locationText,
        boolean autoCreate,
        boolean active,
        boolean shared,
        Integer sharedParticipantCount,
        UUID householdId,
        String householdName,
        OffsetDateTime lastRunAt
    ) {
    }

    public record CalendarItemResponse(
        String kind,
        String title,
        BigDecimal amount,
        LocalDate dueDate,
        String detail
    ) {
    }

    public record BudgetRequest(
        UUID id,
        @NotBlank @Size(max = 140) String title,
        UUID categoryId,
        @NotNull @DecimalMin("0.01") BigDecimal amountLimit,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        int alertPercent,
        boolean rolloverEnabled,
        boolean active
    ) {
    }

    public record BudgetResponse(
        UUID id,
        String title,
        UUID categoryId,
        String categoryName,
        BigDecimal amountLimit,
        LocalDate periodStart,
        LocalDate periodEnd,
        int alertPercent,
        boolean rolloverEnabled,
        boolean active,
        BigDecimal spentAmount,
        BigDecimal remainingAmount,
        double usagePercent
    ) {
    }

    public record SavingsGoalRequest(
        UUID id,
        @NotBlank @Size(max = 140) String title,
        @NotNull @DecimalMin("0.01") BigDecimal targetAmount,
        @NotNull BigDecimal savedAmount,
        LocalDate targetDate,
        UUID accountId,
        @Size(max = 500) String notes,
        boolean active
    ) {
    }

    public record SavingsGoalResponse(
        UUID id,
        String title,
        BigDecimal targetAmount,
        BigDecimal savedAmount,
        LocalDate targetDate,
        UUID accountId,
        String accountName,
        String notes,
        boolean active,
        double progressPercent
    ) {
    }

    public record ReminderResponse(
        String severity,
        String title,
        String message,
        LocalDate dueDate,
        BigDecimal amount
    ) {
    }
}
