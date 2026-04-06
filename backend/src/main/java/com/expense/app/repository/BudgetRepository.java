package com.expense.app.repository;

import com.expense.app.entity.Budget;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    List<Budget> findByUserIdOrderByPeriodStartDesc(UUID userId);

    List<Budget> findByUserIdAndActiveTrueOrderByPeriodStartDesc(UUID userId);

    Optional<Budget> findByIdAndUserId(UUID id, UUID userId);
}
