package com.expense.app.service;

import com.expense.app.dto.CategoryDtos;
import com.expense.app.entity.AppUser;
import com.expense.app.entity.Category;
import com.expense.app.exception.ApiException;
import com.expense.app.exception.NotFoundException;
import com.expense.app.repository.AppUserRepository;
import com.expense.app.repository.CategoryRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final AppUserRepository appUserRepository;
    private final AuditService auditService;

    public List<CategoryDtos.CategoryResponse> tree(UUID userId) {
        List<Category> categories = categoryRepository.findByUserIdAndDeletedAtIsNullOrderBySortOrderAscNameAsc(userId);
        Map<UUID, List<Category>> childrenByParent = categories.stream()
            .filter(category -> category.getParentCategory() != null)
            .collect(Collectors.groupingBy(category -> category.getParentCategory().getId()));

        return categories.stream()
            .filter(category -> category.getParentCategory() == null)
            .sorted(Comparator.comparing(Category::getSortOrder).thenComparing(Category::getName))
            .map(category -> toTree(category, childrenByParent))
            .toList();
    }

    public CategoryDtos.CategoryResponse getOne(UUID userId, UUID id) {
        return flattenTree(tree(userId)).stream()
            .filter(category -> category.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Category not found"));
    }

    @Transactional
    public CategoryDtos.CategoryResponse create(UUID userId, CategoryDtos.CategoryRequest request) {
        AppUser user = appUserRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        Category category = new Category();
        category.setUser(user);
        apply(category, userId, request, null, true);
        category = categoryRepository.save(category);
        auditService.log(user, "category", category.getId(), "CREATE", null, request);
        return getOne(userId, category.getId());
    }

    @Transactional
    public CategoryDtos.CategoryResponse update(UUID userId, UUID id, CategoryDtos.CategoryRequest request) {
        Category category = categoryRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
            .orElseThrow(() -> new NotFoundException("Category not found"));
        CategoryDtos.CategoryResponse oldValue = getOne(userId, id);
        apply(category, userId, request, id, category.isActive());
        category = categoryRepository.save(category);
        auditService.log(category.getUser(), "category", category.getId(), "UPDATE", oldValue, request);
        return getOne(userId, category.getId());
    }

    @Transactional
    public void deactivate(UUID userId, UUID id) {
        Category category = categoryRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
            .orElseThrow(() -> new NotFoundException("Category not found"));
        category.setActive(false);
        categoryRepository.save(category);
        auditService.log(category.getUser(), "category", id, "DEACTIVATE", null, Map.of("active", false));
    }

    private void apply(Category category, UUID userId, CategoryDtos.CategoryRequest request, UUID existingId, boolean defaultActive) {
        Category parent = null;
        if (request.parentCategoryId() != null) {
            parent = categoryRepository.findByIdAndUserIdAndDeletedAtIsNull(request.parentCategoryId(), userId)
                .orElseThrow(() -> new NotFoundException("Parent category not found"));
        }
        ensureUnique(userId, request.name(), request.parentCategoryId(), existingId);
        category.setName(request.name().trim());
        category.setParentCategory(parent);
        category.setCategoryKind(request.categoryKind());
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        category.setActive(request.active() != null ? request.active() : defaultActive);
    }

    private void ensureUnique(UUID userId, String name, UUID parentId, UUID existingId) {
        boolean duplicate = categoryRepository.findByUserIdAndDeletedAtIsNullOrderBySortOrderAscNameAsc(userId).stream()
            .anyMatch(category -> category.getName().trim().equalsIgnoreCase(name.trim())
                && sameParent(category, parentId)
                && (existingId == null || !category.getId().equals(existingId)));
        if (duplicate) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A category with this name already exists under the same parent");
        }
    }

    private boolean sameParent(Category category, UUID parentId) {
        return (category.getParentCategory() == null && parentId == null)
            || (category.getParentCategory() != null && category.getParentCategory().getId().equals(parentId));
    }

    private CategoryDtos.CategoryResponse toTree(Category category, Map<UUID, List<Category>> childrenByParent) {
        List<CategoryDtos.CategoryResponse> children = childrenByParent.getOrDefault(category.getId(), List.of()).stream()
            .sorted(Comparator.comparing(Category::getSortOrder).thenComparing(Category::getName))
            .map(child -> toTree(child, childrenByParent))
            .toList();
        return new CategoryDtos.CategoryResponse(
            category.getId(),
            category.getName(),
            category.getCategoryKind(),
            category.getParentCategory() != null ? category.getParentCategory().getId() : null,
            category.isActive(),
            category.getSortOrder(),
            children
        );
    }

    private List<CategoryDtos.CategoryResponse> flattenTree(List<CategoryDtos.CategoryResponse> tree) {
        List<CategoryDtos.CategoryResponse> result = new ArrayList<>();
        for (CategoryDtos.CategoryResponse node : tree) {
            result.add(node);
            result.addAll(flattenTree(node.children()));
        }
        return result;
    }
}
