package com.expense.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
        @NotBlank @Size(max = 120) String fullName,
        @Email @NotBlank String email,
        @Size(max = 30) String mobile,
        @NotBlank @Size(min = 6, max = 50) String password
    ) {
    }

    public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password
    ) {
    }

    public record AuthResponse(
        String token,
        UserDtos.ProfileResponse profile,
        String message
    ) {
    }
}
