package com.expense.app.enums;

public enum CounterpartyType {
    MERCHANT,
    PERSON,
    BOTH;

    public boolean supportsPersonLedger() {
        return this == PERSON || this == BOTH;
    }
}
