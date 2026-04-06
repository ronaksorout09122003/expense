package com.expense.app.service;

import com.expense.app.dto.AttachmentDtos;
import com.expense.app.entity.TxnAttachment;
import com.expense.app.exception.NotFoundException;
import com.expense.app.repository.TxnAttachmentRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final TxnAttachmentRepository txnAttachmentRepository;
    private final TransactionService transactionService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<AttachmentDtos.AttachmentResponse> list(UUID userId, UUID transactionId) {
        transactionService.getOwnedTransaction(userId, transactionId);
        return txnAttachmentRepository.findByTransactionUserIdAndTransactionIdOrderByCreatedAtDesc(userId, transactionId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public AttachmentDtos.AttachmentResponse add(UUID userId, UUID transactionId, AttachmentDtos.AttachmentRequest request) {
        var transaction = transactionService.getOwnedTransaction(userId, transactionId);
        TxnAttachment attachment = new TxnAttachment();
        attachment.setTransaction(transaction);
        attachment.setFileUrl(request.fileUrl().trim());
        attachment.setMimeType(blankToNull(request.mimeType()));
        attachment.setLabel(blankToNull(request.label()));
        attachment = txnAttachmentRepository.save(attachment);
        auditService.log(transaction.getUser(), "txn_attachment", attachment.getId(), "CREATE", null, request);
        return toResponse(attachment);
    }

    @Transactional
    public void delete(UUID userId, UUID transactionId, UUID attachmentId) {
        TxnAttachment attachment = txnAttachmentRepository.findByIdAndTransactionIdAndTransactionUserId(attachmentId, transactionId, userId)
            .orElseThrow(() -> new NotFoundException("Attachment not found"));
        auditService.log(attachment.getTransaction().getUser(), "txn_attachment", attachment.getId(), "DELETE", toResponse(attachment), null);
        txnAttachmentRepository.delete(attachment);
    }

    private AttachmentDtos.AttachmentResponse toResponse(TxnAttachment attachment) {
        return new AttachmentDtos.AttachmentResponse(
            attachment.getId(),
            attachment.getFileUrl(),
            attachment.getMimeType(),
            attachment.getLabel(),
            attachment.getCreatedAt()
        );
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
