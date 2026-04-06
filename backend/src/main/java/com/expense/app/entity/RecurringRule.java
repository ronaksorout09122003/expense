package com.expense.app.entity;

import com.expense.app.enums.RecurringFrequency;
import com.expense.app.enums.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "recurring_rule")
public class RecurringRule extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 140)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionType transactionType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_account_id")
    private Account toAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_id")
    private Counterparty counterparty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RecurringFrequency frequencyType;

    private OffsetDateTime nextRunAt;

    @Column(nullable = false)
    private Integer intervalDays = 30;

    @Column(nullable = false)
    private Integer dueDateOffsetDays = 0;

    @Column(nullable = false)
    private Integer remindDaysBefore = 0;

    @Column(length = 500)
    private String note;

    @Column(length = 100)
    private String referenceNo;

    @Column(length = 180)
    private String locationText;

    @Column(nullable = false)
    private boolean autoCreate;

    private OffsetDateTime lastRunAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_shared", nullable = false)
    private boolean shared;

    @Column(nullable = false)
    private Integer sharedParticipantCount = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id")
    private Household household;
}
