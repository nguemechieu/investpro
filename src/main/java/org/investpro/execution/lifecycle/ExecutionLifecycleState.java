package org.investpro.execution.lifecycle;

public enum ExecutionLifecycleState {
    CREATED,
    SUBMITTED,
    ACCEPTED,
    PARTIALLY_FILLED,
    FILLED,
    REJECTED,
    CANCELLED,
    EXPIRED,
    BLOCKCHAIN_PENDING,
    BLOCKCHAIN_CONFIRMED,
    BLOCKCHAIN_FAILED
}
