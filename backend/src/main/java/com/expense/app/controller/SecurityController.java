package com.expense.app.controller;

import com.expense.app.dto.CommonDtos;
import com.expense.app.dto.SecurityDtos;
import com.expense.app.security.UserPrincipal;
import com.expense.app.service.SecurityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security")
@RequiredArgsConstructor
public class SecurityController {

    private final SecurityService securityService;

    @GetMapping
    public SecurityDtos.SecurityOverviewResponse overview(@AuthenticationPrincipal UserPrincipal principal) {
        return securityService.overview(principal.getUserId());
    }

    @PutMapping("/pin")
    public CommonDtos.MessageResponse setPin(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody SecurityDtos.PinRequest request
    ) {
        return securityService.setPin(principal.getUserId(), request);
    }

    @DeleteMapping("/pin")
    public CommonDtos.MessageResponse clearPin(@AuthenticationPrincipal UserPrincipal principal) {
        return securityService.clearPin(principal.getUserId());
    }

    @PostMapping("/pin/verify")
    public SecurityDtos.PinVerificationResponse verifyPin(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody SecurityDtos.PinRequest request
    ) {
        return securityService.verifyPin(principal.getUserId(), request);
    }
}
