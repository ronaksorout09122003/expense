package com.expense.app.repository;

import com.expense.app.entity.TransactionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    List<TransactionEntity> findByUserIdAndDeletedAtIsNullOrderByTransactionAtDesc(UUID userId);

    List<TransactionEntity> findByHouseholdIdAndSharedTrueAndDeletedAtIsNullOrderByTransactionAtDesc(UUID householdId);

    Optional<TransactionEntity> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
}
