package com.expense.app.dto;

import com.expense.app.enums.CategoryKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    public record CategoryRequest(
        @NotBlank @Size(max = 120) String name,
        UUID parentCategoryId,
        @NotNull CategoryKind categoryKind,
        Integer sortOrder,
        Boolean active
    ) {
    }

    public record CategoryResponse(
        UUID id,
        String name,
        CategoryKind categoryKind,
        UUID parentCategoryId,
        boolean active,
        int sortOrder,
        List<CategoryResponse> children
    ) {
    }
}
