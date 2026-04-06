package com.expense.app.service;

import com.expense.app.dto.AuditDtos;
import com.expense.app.repository.AuditLogRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public List<AuditDtos.AuditEntryResponse> list(UUID userId) {
        return auditLogRepository.findTop100ByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(log -> new AuditDtos.AuditEntryResponse(
                log.getId(),
                log.getEntityName(),
                log.getEntityId(),
                log.getActionName(),
                log.getOldValue(),
                log.getNewValue(),
                log.getCreatedAt()
            ))
            .toList();
    }
}
