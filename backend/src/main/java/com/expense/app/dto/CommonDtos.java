package com.expense.app.dto;

import java.util.List;

public final class CommonDtos {

    private CommonDtos() {
    }

    public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
    }

    public record MessageResponse(String message) {
    }
}
