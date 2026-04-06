package com.expense.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class AttachmentDtos {

    private AttachmentDtos() {
    }

    public record AttachmentRequest(
        @NotBlank @Size(max = 500) String fileUrl,
        @Size(max = 120) String mimeType,
        @Size(max = 140) String label
    ) {
    }

    public record AttachmentResponse(
        UUID id,
        String fileUrl,
        String mimeType,
        String label,
        OffsetDateTime createdAt
    ) {
    }
}
