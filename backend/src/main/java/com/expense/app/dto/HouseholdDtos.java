package com.expense.app.dto;

import com.expense.app.enums.HouseholdMemberRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class HouseholdDtos {

    private HouseholdDtos() {
    }

    public record CreateHouseholdRequest(
        @NotBlank @Size(max = 140) String name
    ) {
    }

    public record JoinHouseholdRequest(
        @NotBlank @Size(max = 24) String inviteCode
    ) {
    }

    public record HouseholdMemberResponse(
        UUID userId,
        String fullName,
        String email,
        HouseholdMemberRole role
    ) {
    }

    public record SharedTransactionResponse(
        UUID transactionId,
        String ownerName,
        String transactionType,
        BigDecimal amount,
        BigDecimal estimatedShare,
        String note,
        OffsetDateTime transactionAt,
        int participantCount
    ) {
    }

    public record HouseholdResponse(
        UUID id,
        String name,
        String inviteCode,
        List<HouseholdMemberResponse> members,
        List<SharedTransactionResponse> recentSharedTransactions,
        BigDecimal totalSharedSpend
    ) {
    }

    public record CurrentHouseholdResponse(
        boolean joined,
        HouseholdResponse household
    ) {
    }
}
