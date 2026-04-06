package com.expense.app.controller;

import com.expense.app.dto.UserDtos;
import com.expense.app.security.UserPrincipal;
import com.expense.app.service.AuthService;
import com.expense.app.service.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;
    private final SettingsService settingsService;

    @GetMapping("/api/v1/me")
    public UserDtos.ProfileResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.me(principal);
    }

    @GetMapping("/api/v1/settings")
    public UserDtos.SettingsResponse settings(@AuthenticationPrincipal UserPrincipal principal) {
        return settingsService.getSettings(principal.getUserId());
    }

    @PutMapping("/api/v1/settings")
    public UserDtos.SettingsResponse updateSettings(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody UserDtos.UpdateSettingsRequest request
    ) {
        return settingsService.updateSettings(principal.getUserId(), request);
    }
}
