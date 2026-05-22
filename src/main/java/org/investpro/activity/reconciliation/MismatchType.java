package org.investpro.activity.reconciliation;

public enum MismatchType {
    MISSING_LOCAL,
    MISSING_BROKER,
    STATUS_MISMATCH,
    QUANTITY_MISMATCH,
    PRICE_MISMATCH,
    BALANCE_DISCREPANCY,
    DUPLICATE_EVENT,
    UNKNOWN
}
