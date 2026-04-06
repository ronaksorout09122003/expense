package com.expense.app.repository;

import com.expense.app.entity.RecurringRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringRuleRepository extends JpaRepository<RecurringRule, UUID> {

    List<RecurringRule> findByUserIdOrderByNextRunAtAsc(UUID userId);

    List<RecurringRule> findByUserIdAndActiveTrueOrderByNextRunAtAsc(UUID userId);

    Optional<RecurringRule> findByIdAndUserId(UUID id, UUID userId);
}
