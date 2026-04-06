package com.expense.app.controller;

import com.expense.app.dto.AttachmentDtos;
import com.expense.app.dto.CommonDtos;
import com.expense.app.security.UserPrincipal;
import com.expense.app.service.AttachmentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions/{transactionId}/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @GetMapping
    public List<AttachmentDtos.AttachmentResponse> list(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID transactionId
    ) {
        return attachmentService.list(principal.getUserId(), transactionId);
    }

    @PostMapping
    public AttachmentDtos.AttachmentResponse add(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID transactionId,
        @Valid @RequestBody AttachmentDtos.AttachmentRequest request
    ) {
        return attachmentService.add(principal.getUserId(), transactionId, request);
    }

    @DeleteMapping("/{attachmentId}")
    public CommonDtos.MessageResponse delete(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID transactionId,
        @PathVariable UUID attachmentId
    ) {
        attachmentService.delete(principal.getUserId(), transactionId, attachmentId);
        return new CommonDtos.MessageResponse("Attachment removed");
    }
}
