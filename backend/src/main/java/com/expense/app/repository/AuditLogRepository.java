package com.expense.app.repository;

import com.expense.app.entity.AuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findTop100ByUserIdOrderByCreatedAtDesc(UUID userId);
}
