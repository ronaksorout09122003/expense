package com.expense.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class SecurityDtos {

    private SecurityDtos() {
    }

    public record SecurityOverviewResponse(
        boolean hasPin,
        boolean biometricEnabled,
        boolean reminderEnabled,
        int sessionTimeoutMinutes
    ) {
    }

    public record PinRequest(
        @NotBlank @Pattern(regexp = "\\d{4,8}") String pin
    ) {
    }

    public record PinVerificationResponse(
        boolean valid,
        String message
    ) {
    }
}
