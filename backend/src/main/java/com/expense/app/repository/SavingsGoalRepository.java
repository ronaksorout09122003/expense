package com.expense.app.repository;

import com.expense.app.entity.SavingsGoal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, UUID> {

    List<SavingsGoal> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<SavingsGoal> findByUserIdAndActiveTrueOrderByCreatedAtDesc(UUID userId);

    Optional<SavingsGoal> findByIdAndUserId(UUID id, UUID userId);
}
