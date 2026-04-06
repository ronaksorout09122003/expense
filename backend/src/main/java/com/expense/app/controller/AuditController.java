package com.expense.app.controller;

import com.expense.app.dto.AuditDtos;
import com.expense.app.security.UserPrincipal;
import com.expense.app.service.AuditQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditQueryService auditQueryService;

    @GetMapping
    public List<AuditDtos.AuditEntryResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return auditQueryService.list(principal.getUserId());
    }
}
