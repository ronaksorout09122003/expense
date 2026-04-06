package com.expense.app.repository;

import com.expense.app.entity.TxnAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TxnAttachmentRepository extends JpaRepository<TxnAttachment, UUID> {

    List<TxnAttachment> findByTransactionUserIdAndTransactionIdOrderByCreatedAtDesc(UUID userId, UUID transactionId);

    Optional<TxnAttachment> findByIdAndTransactionIdAndTransactionUserId(UUID id, UUID transactionId, UUID userId);
}
