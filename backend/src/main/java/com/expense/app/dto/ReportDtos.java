package com.expense.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record SummaryPointResponse(
        String label,
        BigDecimal amount,
        long count
    ) {
    }

    public record TimelinePointResponse(
        LocalDate date,
        BigDecimal incoming,
        BigDecimal outgoing,
        BigDecimal net
    ) {
    }

    public record DashboardResponse(
        BigDecimal todayExpense,
        BigDecimal yesterdayExpense,
        BigDecimal currentMonthExpense,
        BigDecimal cashBalance,
        BigDecimal nonCashBalance,
        BigDecimal receivableOutstanding,
        BigDecimal payableOutstanding,
        List<TransactionDtos.TransactionSummaryResponse> recentTransactions,
        List<SummaryPointResponse> topCategories,
        long overdueCount
    ) {
    }

    public record PeriodComparisonResponse(
        BigDecimal currentExpense,
        BigDecimal previousExpense,
        BigDecimal deltaAmount,
        double deltaPercent
    ) {
    }

    public record ForecastResponse(
        BigDecimal averageDailyExpense,
        BigDecimal averageDailyIncome,
        BigDecimal projectedMonthExpense,
        BigDecimal projectedMonthIncome,
        BigDecimal projectedMonthNet
    ) {
    }
}
