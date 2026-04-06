package com.expense.app.service;

import com.expense.app.entity.AppUser;
import com.expense.app.entity.AuditLog;
import com.expense.app.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public void log(AppUser user, String entityName, UUID entityId, String actionName, Object oldValue, Object newValue) {
        AuditLog log = new AuditLog();
        log.setUser(user);
        log.setEntityName(entityName);
        log.setEntityId(entityId);
        log.setActionName(actionName);
        log.setOldValue(asJson(oldValue));
        log.setNewValue(asJson(newValue));
        auditLogRepository.save(log);
    }

    private String asJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return String.valueOf(value);
        }
    }
}
