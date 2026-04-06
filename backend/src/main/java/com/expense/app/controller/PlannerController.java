package com.expense.app.controller;

import com.expense.app.dto.PlannerDtos;
import com.expense.app.dto.TransactionDtos;
import com.expense.app.security.UserPrincipal;
import com.expense.app.service.PlannerService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/planner")
@RequiredArgsConstructor
public class PlannerController {

    private final PlannerService plannerService;

    @GetMapping("/recurring")
    public List<PlannerDtos.RecurringRuleResponse> recurring(@AuthenticationPrincipal UserPrincipal principal) {
        return plannerService.recurringRules(principal.getUserId());
    }

    @PostMapping("/recurring")
    public PlannerDtos.RecurringRuleResponse saveRecurring(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody PlannerDtos.RecurringRuleRequest request
    ) {
        return plannerService.saveRecurringRule(principal.getUserId(), request);
    }

    @PostMapping("/recurring/{id}/run")
    public TransactionDtos.TransactionDetailResponse runRecurring(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID id
    ) {
        return plannerService.runRecurringRule(principal.getUserId(), id);
    }

    @GetMapping("/calendar")
    public List<PlannerDtos.CalendarItemResponse> calendar(@AuthenticationPrincipal UserPrincipal principal) {
        return plannerService.calendar(principal.getUserId());
    }

    @GetMapping("/budgets")
    public List<PlannerDtos.BudgetResponse> budgets(@AuthenticationPrincipal UserPrincipal principal) {
        return plannerService.budgets(principal.getUserId());
    }

    @PostMapping("/budgets")
    public PlannerDtos.BudgetResponse saveBudget(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody PlannerDtos.BudgetRequest request
    ) {
        return plannerService.saveBudget(principal.getUserId(), request);
    }

    @GetMapping("/goals")
    public List<PlannerDtos.SavingsGoalResponse> goals(@AuthenticationPrincipal UserPrincipal principal) {
        return plannerService.goals(principal.getUserId());
    }

    @PostMapping("/goals")
    public PlannerDtos.SavingsGoalResponse saveGoal(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody PlannerDtos.SavingsGoalRequest request
    ) {
        return plannerService.saveGoal(principal.getUserId(), request);
    }

    @GetMapping("/reminders")
    public List<PlannerDtos.ReminderResponse> reminders(@AuthenticationPrincipal UserPrincipal principal) {
        return plannerService.reminders(principal.getUserId());
    }
}
