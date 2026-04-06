package com.expense.app.repository;

import com.expense.app.entity.Account;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByUserIdAndDeletedAtIsNullOrderByNameAsc(UUID userId);

    Optional<Account> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
}
