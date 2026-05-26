package org.investpro.execution;

import org.investpro.risk.RiskDecision;

public interface OrderRouter {
    OrderRouteResult route(OrderIntent intent, RiskDecision riskDecision, ExecutionGuardDecision guardDecision);
}
