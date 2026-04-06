package com.expense.app.dto;

import com.expense.app.enums.CounterpartyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class CounterpartyDtos {

    private CounterpartyDtos() {
    }

    public record CounterpartyRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull CounterpartyType counterpartyType,
        @Size(max = 30) String phone,
        @Size(max = 500) String notes,
        Boolean active
    ) {
    }

    public record CounterpartyResponse(
        UUID id,
        String name,
        CounterpartyType counterpartyType,
        String phone,
        String notes,
        boolean active
    ) {
    }
}
