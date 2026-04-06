package com.expense.app.controller;

import com.expense.app.dto.CategoryDtos;
import com.expense.app.dto.CommonDtos;
import com.expense.app.security.UserPrincipal;
import com.expense.app.service.CategoryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/tree")
    public List<CategoryDtos.CategoryResponse> tree(@AuthenticationPrincipal UserPrincipal principal) {
        return categoryService.tree(principal.getUserId());
    }

    @PostMapping
    public CategoryDtos.CategoryResponse create(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CategoryDtos.CategoryRequest request
    ) {
        return categoryService.create(principal.getUserId(), request);
    }

    @PutMapping("/{id}")
    public CategoryDtos.CategoryResponse update(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID id,
        @Valid @RequestBody CategoryDtos.CategoryRequest request
    ) {
        return categoryService.update(principal.getUserId(), id, request);
    }

    @DeleteMapping("/{id}")
    public CommonDtos.MessageResponse deactivate(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        categoryService.deactivate(principal.getUserId(), id);
        return new CommonDtos.MessageResponse("Category deactivated");
    }
}
