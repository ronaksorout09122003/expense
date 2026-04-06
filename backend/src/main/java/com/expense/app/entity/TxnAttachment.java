package com.expense.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "txn_attachment")
public class TxnAttachment extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "txn_id", nullable = false)
    private TransactionEntity transaction;

    @Column(nullable = false, length = 500)
    private String fileUrl;

    @Column(length = 120)
    private String mimeType;

    @Column(length = 140)
    private String label;
}
