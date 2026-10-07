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



        try {
            var pair = exchange.parsePair(plan.getSymbol());
            Side side = "SELL".equalsIgnoreCase(plan.getSide()) ? Side.SELL : Side.BUY;
            String orderType = plan.getOrderType() == null ? "MARKET" : plan.getOrderType().toUpperCase();
            var execution = plan.getExecutionMode().isLocal()
                    ? exchange.localPaperOrderExecution() : exchange.botOrderExecution();

            return exchange.executeRiskApproved(plan.isRiskApproved(), () -> switch (orderType) {
                case "LIMIT" -> execution.createLimitOrder(pair, side, plan.getUnits(), plan.getEntryPrice());
                case "STOP" -> execution.createStopOrder(pair, side, plan.getUnits(), plan.getStopLoss());
                case "STOP_LIMIT", "TRAILING_STOP" -> CompletableFuture.failedFuture(
                        new UnsupportedOperationException("Native IBKR " + orderType + " transmission is not implemented."));
                case "BRACKET" -> execution.createBracketOrder(
                        pair,
                        side,
                        plan.getUnits(),
                        plan.getEntryPrice(),
                        plan.getStopLoss(),
                        plan.getTakeProfit());
                default -> execution.createMarketOrder(pair, side, plan.getUnits());
            });
        } catch (SQLException | ClassNotFoundException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }
}
