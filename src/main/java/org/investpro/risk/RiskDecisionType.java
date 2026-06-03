package org.investpro.risk;

import lombok.Getter;

@Getter
public enum RiskDecisionType {
    APPROVE,
    REJECT,
    REDUCE_SIZE,
    CLOSE,
    HOLD,
    WAIT
}
