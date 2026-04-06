package com.expense.app.repository;

import com.expense.app.entity.Counterparty;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CounterpartyRepository extends JpaRepository<Counterparty, UUID> {

    List<Counterparty> findByUserIdAndDeletedAtIsNullOrderByNameAsc(UUID userId);

    Optional<Counterparty> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
}
