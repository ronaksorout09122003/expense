package com.expense.app.controller;

import com.expense.app.dto.CommonDtos;
import com.expense.app.dto.HouseholdDtos;
import com.expense.app.security.UserPrincipal;
import com.expense.app.service.HouseholdService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/household")
@RequiredArgsConstructor
public class HouseholdController {

    private final HouseholdService householdService;

    @GetMapping
    public HouseholdDtos.CurrentHouseholdResponse current(@AuthenticationPrincipal UserPrincipal principal) {
        return householdService.current(principal.getUserId());
    }

    @PostMapping("/create")
    public HouseholdDtos.HouseholdResponse create(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody HouseholdDtos.CreateHouseholdRequest request
    ) {
        return householdService.create(principal.getUserId(), request);
    }

    @PostMapping("/join")
    public HouseholdDtos.HouseholdResponse join(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody HouseholdDtos.JoinHouseholdRequest request
    ) {
        return householdService.join(principal.getUserId(), request);
    }

    @PostMapping("/leave")
    public CommonDtos.MessageResponse leave(@AuthenticationPrincipal UserPrincipal principal) {
        return householdService.leave(principal.getUserId());
    }
}
