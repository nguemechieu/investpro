package org.investpro.execution;

import org.investpro.market.MarketContext;
import org.investpro.risk.RiskDecision;

import java.util.List;

public class ExecutionGuard {
    public ExecutionGuardDecision check(OrderIntent intent, RiskDecision riskDecision, MarketContext context) {
        if (intent == null) {
            return ExecutionGuardDecision.blocked("MISSING_INTENT", "Order intent is required.");
        }
        if (riskDecision == null || !riskDecision.canProceed()) {
            return ExecutionGuardDecision.blocked("RISK_NOT_APPROVED", "Risk decision does not allow execution.");
        }
        if (context == null) {
            return ExecutionGuardDecision.blocked("MISSING_CONTEXT", "Market context is required.");
        }
        if (context.dataFreshnessStatus() != MarketContext.DataFreshnessStatus.FRESH) {
            return ExecutionGuardDecision.blocked("STALE_MARKET_DATA", "Market data is stale.");
        }
        if (context.accountSnapshot().stale()) {
            return ExecutionGuardDecision.blocked("STALE_ACCOUNT_STATE", "Account state is stale.");
        }
        if (!context.exchangeCapabilities().connected()) {
            return ExecutionGuardDecision.blocked("EXCHANGE_DISCONNECTED", "Exchange connection is down.");
        }
        if (!context.exchangeCapabilities().websocketHealthy()) {
            return ExecutionGuardDecision.blocked("WEBSOCKET_UNHEALTHY", "Market stream is unhealthy.");
        }
        if (context.hasPendingOrder()) {
            return ExecutionGuardDecision.blocked("DUPLICATE_PENDING_ORDER", "Pending order already exists for symbol.");
        }
        if ("LIMIT".equalsIgnoreCase(intent.orderType()) && !context.exchangeCapabilities().supportsLimitOrders()) {
            return ExecutionGuardDecision.blocked("UNSUPPORTED_ORDER_TYPE", "Exchange does not support limit orders.");
        }
        return ExecutionGuardDecision.allowed(List.of());
    }
}
