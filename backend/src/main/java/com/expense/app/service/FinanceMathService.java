package com.expense.app.service;

import com.expense.app.entity.Account;
import com.expense.app.entity.TransactionEntity;
import com.expense.app.entity.TxnSettlement;
import com.expense.app.enums.TransactionStatus;
import com.expense.app.enums.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class FinanceMathService {

    public Map<UUID, BigDecimal> calculateAccountBalances(List<Account> accounts, List<TransactionEntity> transactions) {
        Map<UUID, BigDecimal> balances = new HashMap<>();
        for (Account account : accounts) {
            balances.put(account.getId(), normalize(account.getOpeningBalance()));
        }
        for (TransactionEntity transaction : transactions) {
            if (!isEffective(transaction)) {
                continue;
            }
            BigDecimal amount = normalize(transaction.getAmount());
            switch (transaction.getTransactionType()) {
                case EXPENSE, LEND, REPAYMENT_OUT -> decrement(balances, transaction.getFromAccount(), amount);
                case INCOME, BORROW, REPAYMENT_IN -> increment(balances, transaction.getToAccount(), amount);
                case TRANSFER -> {
                    decrement(balances, transaction.getFromAccount(), amount);
                    increment(balances, transaction.getToAccount(), amount);
                }
            }
        }
        return balances;
    }

    public Map<UUID, BigDecimal> calculateOutstandingByBaseTxn(List<TransactionEntity> transactions, List<TxnSettlement> settlements) {
        Map<UUID, BigDecimal> outstanding = new HashMap<>();
        Map<UUID, TransactionEntity> byId = new HashMap<>();
        for (TransactionEntity transaction : transactions) {
            byId.put(transaction.getId(), transaction);
            if (isEffective(transaction) && (transaction.getTransactionType() == TransactionType.LEND || transaction.getTransactionType() == TransactionType.BORROW)) {
                outstanding.put(transaction.getId(), normalize(transaction.getAmount()));
            }
        }
        for (TxnSettlement settlement : settlements) {
            TransactionEntity base = byId.get(settlement.getBaseTxn().getId());
            TransactionEntity settlementTxn = byId.get(settlement.getSettlementTxn().getId());
            if (base == null || settlementTxn == null || !isEffective(base) || !isEffective(settlementTxn)) {
                continue;
            }
            outstanding.computeIfPresent(base.getId(), (id, amount) -> normalize(amount.subtract(normalize(settlement.getSettledAmount()))));
        }
        outstanding.replaceAll((id, value) -> value.signum() < 0 ? BigDecimal.ZERO : normalize(value));
        return outstanding;
    }

    public boolean isOverdue(TransactionEntity transaction, BigDecimal outstandingAmount) {
        return outstandingAmount != null
            && outstandingAmount.signum() > 0
            && transaction.getDueDate() != null
            && transaction.getDueDate().isBefore(LocalDate.now());
    }

    public boolean isEffective(TransactionEntity transaction) {
        return transaction.getDeletedAt() == null && transaction.getStatus() == TransactionStatus.ACTIVE;
    }

    public BigDecimal normalize(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
    }

    private void increment(Map<UUID, BigDecimal> balances, Account account, BigDecimal amount) {
        if (account != null) {
            balances.merge(account.getId(), amount, BigDecimal::add);
        }
    }

    private void decrement(Map<UUID, BigDecimal> balances, Account account, BigDecimal amount) {
        if (account != null) {
            balances.merge(account.getId(), amount.negate(), BigDecimal::add);
        }
    }
}
