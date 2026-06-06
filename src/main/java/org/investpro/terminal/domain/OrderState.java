package org.investpro.terminal.domain;

public enum OrderState {
    DRAFT,
    SUBMITTED,
    ACCEPTED,
    PARTIALLY_FILLED,
    FILLED,
    CANCELED,
    REJECTED,
    EXPIRED,
    REPLACED,
    UNKNOWN
}
