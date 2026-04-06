package com.expense.app.controller;

import com.expense.app.dto.ReportDtos;
import com.expense.app.security.UserPrincipal;
import com.expense.app.service.ReportService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/dashboard")
    public ReportDtos.DashboardResponse dashboard(@AuthenticationPrincipal UserPrincipal principal) {
        return reportService.dashboard(principal.getUserId());
    }

    @GetMapping("/category-summary")
    public List<ReportDtos.SummaryPointResponse> categorySummary(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return reportService.categorySummary(principal.getUserId(), from, to);
    }

    @GetMapping("/subcategory-summary")
    public List<ReportDtos.SummaryPointResponse> subcategorySummary(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) UUID parentCategoryId
    ) {
        return reportService.subcategorySummary(principal.getUserId(), from, to, parentCategoryId);
    }

    @GetMapping("/account-summary")
    public List<ReportDtos.SummaryPointResponse> accountSummary(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return reportService.accountSummary(principal.getUserId(), from, to);
    }

    @GetMapping("/counterparty-summary")
    public List<ReportDtos.SummaryPointResponse> counterpartySummary(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return reportService.counterpartySummary(principal.getUserId(), from, to);
    }

    @GetMapping("/cash-flow")
    public List<ReportDtos.TimelinePointResponse> cashFlow(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return reportService.cashFlow(principal.getUserId(), from, to);
    }

    @GetMapping("/comparison")
    public ReportDtos.PeriodComparisonResponse comparison(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return reportService.periodComparison(principal.getUserId(), from, to);
    }

    @GetMapping("/forecast")
    public ReportDtos.ForecastResponse forecast(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return reportService.forecast(principal.getUserId(), from, to);
    }
}
