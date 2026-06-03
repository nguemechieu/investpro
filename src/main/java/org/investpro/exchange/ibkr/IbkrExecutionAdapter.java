package org.investpro.exchange.ibkr;

import lombok.extern.slf4j.Slf4j;
import org.investpro.risk.RiskEngine;
import org.investpro.strategy.execution.ExecutionPlan;
import org.investpro.strategy.execution.ExecutionRouter;
import org.investpro.strategy.execution.ExecutionVenue;
import org.investpro.utils.Side;

import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

@Slf4j
public final class IbkrExecutionAdapter {

    private final IbkrExchange exchange;

    public IbkrExecutionAdapter(IbkrExchange exchange) {
        this.exchange = exchange;
    }

    public CompletableFuture<String> execute(ExecutionPlan plan,
            ExecutionRouter router,
            RiskEngine riskEngine) {
        if (plan == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("ExecutionPlan must not be null"));
        }
        if (router == null || riskEngine == null) {
            return CompletableFuture
                    .failedFuture(new IllegalArgumentException("ExecutionRouter and RiskEngine are required"));
        }

        ExecutionVenue venue = plan.getVenue() == null ? ExecutionVenue.INTERACTIVE_BROKERS : plan.getVenue();
        if (venue != ExecutionVenue.INTERACTIVE_BROKERS) {
            return CompletableFuture
                    .failedFuture(new IllegalStateException("Execution venue is not Interactive Brokers"));
        }

        if (!plan.isRiskApproved()) {
            return CompletableFuture
                    .failedFuture(new IllegalStateException("RiskEngine approval required before IBKR execution"));
        }

        exchange.setLiveRiskApprovalGate(() -> plan.isRiskApproved());

        try {
            var pair = exchange.parsePair(plan.getSymbol());
            Side side = "SELL".equalsIgnoreCase(plan.getSide()) ? Side.SELL : Side.BUY;
            String orderType = plan.getOrderType() == null ? "MARKET" : plan.getOrderType().toUpperCase();

            return switch (orderType) {
                case "LIMIT" -> exchange.createLimitOrder(pair, side, plan.getUnits(), plan.getEntryPrice());
                case "STOP" -> exchange.createStopOrder(pair, side, plan.getUnits(), plan.getStopLoss());
                case "STOP_LIMIT" -> CompletableFuture.completedFuture(
                        exchange.getOrderService().submitStopLimit(pair, side, plan.getUnits(), plan.getStopLoss(),
                                plan.getEntryPrice()));
                case "TRAILING_STOP" -> CompletableFuture.completedFuture(
                        exchange.getOrderService().submitTrailingStop(pair, side, plan.getUnits(),
                                plan.getSlippageTolerance()));
                case "BRACKET" -> exchange.createBracketOrder(
                        pair,
                        side,
                        plan.getUnits(),
                        plan.getEntryPrice(),
                        plan.getStopLoss(),
                        plan.getTakeProfit());
                default -> exchange.createMarketOrder(pair, side, plan.getUnits());
            };
        } catch (SQLException | ClassNotFoundException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }
}
