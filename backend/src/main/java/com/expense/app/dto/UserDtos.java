package com.expense.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    public record ProfileResponse(
        UUID id,
        String fullName,
        String email,
        String mobile,
        String currencyCode,
        String timezone
    ) {
    }

    public record SettingsResponse(
        UUID id,
        UUID defaultAccountId,
        String defaultCurrency,
        String dateFormat,
        boolean biometricEnabled,
        boolean reminderEnabled,
        int sessionTimeoutMinutes
    ) {
    }

    public record UpdateSettingsRequest(
        UUID defaultAccountId,
        @NotBlank @Size(max = 8) String defaultCurrency,
        @NotBlank @Size(max = 30) String dateFormat,
        boolean biometricEnabled,
        boolean reminderEnabled,
        int sessionTimeoutMinutes
    ) {
    }
}
