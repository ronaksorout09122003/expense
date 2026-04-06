package com.expense.app.enums;

public enum TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER,
    LEND,
    BORROW,
    REPAYMENT_IN,
    REPAYMENT_OUT;

    public boolean requiresCounterpartyPerson() {
        return this == LEND || this == BORROW || this == REPAYMENT_IN || this == REPAYMENT_OUT;
    }

    public boolean isReceivableBase() {
        return this == LEND;
    }

    public boolean isPayableBase() {
        return this == BORROW;
    }

    public boolean isSettlement() {
        return this == REPAYMENT_IN || this == REPAYMENT_OUT;
    }

    public boolean countsAsExpense() {
        return this == EXPENSE;
    }

    public boolean countsAsIncome() {
        return this == INCOME;
    }
}
