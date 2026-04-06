package com.expense.app.repository;

import com.expense.app.entity.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByUserIdAndDeletedAtIsNullOrderBySortOrderAscNameAsc(UUID userId);

    Optional<Category> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
}
