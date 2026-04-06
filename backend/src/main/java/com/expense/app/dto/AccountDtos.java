package com.expense.app.dto;

import com.expense.app.enums.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record AccountRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull AccountType accountType,
        @NotNull @DecimalMin("0.00") BigDecimal openingBalance,
        @Size(max = 20) String accentColor,
        Boolean active
    ) {
    }

    public record AccountResponse(
        UUID id,
        String name,
        AccountType accountType,
        BigDecimal openingBalance,
        BigDecimal currentBalance,
        String accentColor,
        boolean active
    ) {
    }
}
