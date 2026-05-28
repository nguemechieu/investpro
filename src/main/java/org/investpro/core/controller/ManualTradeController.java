package org.investpro.core.controller;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.pipeline.TradeDecisionPipeline;
import org.investpro.core.pipeline.TradeRiskContextBuilder;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.Trade;
import org.investpro.risk.TradeRiskContext;
import org.investpro.risk.RiskDecision;
import org.investpro.service.TradingService;

import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * ManualTradeController handles BUY/SELL/CLOSE requests from the TradingWindow
 * UI.
 * <p>
 * This controller enforces that ALL manual trades go through:
 * 1. Risk evaluation (TradeDecisionPipeline)
 * 2. Risk approval (RiskDecision)
 * 3. Execution engine (only if approved)
 * <p>
 * Users cannot bypass risk management even for manual trades.
 * <p>
 * This controller does NOT:
 * - Create exchanges (SystemCore does)
 * - Execute without risk approval
 * - Make risk decisions (RiskManagementSystem does)
 * - Display UI (TradingWindow does)
 */
@Slf4j
public record ManualTradeController(TradeDecisionPipeline tradeDecisionPipeline, Exchange exchange,
                                    TradingService tradingService) {

    public ManualTradeController(
            @NotNull TradeDecisionPipeline tradeDecisionPipeline,

            @NotNull Exchange exchange,
            @NotNull TradingService tradingService) {
        this.tradeDecisionPipeline = Objects.requireNonNull(tradeDecisionPipeline);

        this.exchange = Objects.requireNonNull(exchange);
        this.tradingService = Objects.requireNonNull(tradingService);
    }

    /**
     * Execute a BUY order from the UI.
     * <p>
     * Flow:
     * 1. Build TradeRiskContext from symbol + quantity
     * 2. Evaluate through RiskDecisionPipeline
     * 3. If approved, execute through OrderExecutionService
     * 4. Return OpenOrder on success or throw on rejection
     *
     * @param symbol   the symbol to buy (e.g., "BTC/USDT")
     * @param quantity the quantity to buy
     * @return CompletableFuture<OpenOrder> with the placed order
     */
    public CompletableFuture<Trade> executeBuy(
            @NotNull String symbol,
            double quantity) {
        return executeManualTrade(symbol, "BUY", quantity);
    }

    /**
     * Execute a SELL order from the UI.
     *
     * @param symbol   the symbol to sell
     * @param quantity the quantity to sell
     * @return CompletableFuture<OpenOrder> with the placed order
     */
    public CompletableFuture<Trade> executeSell(
            @NotNull String symbol,
            double quantity) {
        return executeManualTrade(symbol, "SELL", quantity);
    }

    /**
     * Close a position for the symbol.
     *
     * @param symbol the symbol position to close
     * @return CompletableFuture<OpenOrder> with the close order
     */
    public CompletableFuture<Trade> executeClose(@NotNull String symbol) {
        // Closing uses opposite direction to existing position
        // For simplicity, try SELL; ExecutionEngine will validate position exists
        return executeManualTrade(symbol, "SELL", 0.0);
    }

    /**
     * Internal: Execute a manual trade after validation.
     *
     * @param symbol   the trading pair
     * @param action   BUY or SELL
     * @param quantity the quantity (0 means close position)
     * @return CompletableFuture with the order result
     */
    private CompletableFuture<Trade> executeManualTrade(
            @NotNull String symbol,
            @NotNull String action,
            double quantity) {

        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(action, "action must not be null");

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("ManualTradeController: {} {} {}", action, quantity, symbol);

                // Step 1: Build TradeRiskContext from manual trade inputs
                TradeRiskContext riskContext = TradeRiskContextBuilder.fromManualTrade(
                        symbol,
                        action,
                        quantity,
                        exchange,
                        tradingService.getAccount());

                // Step 2: Evaluate through risk pipeline
                RiskDecision riskDecision = tradeDecisionPipeline.evaluate(riskContext);

                if (riskDecision == null) {
                    throw new RuntimeException("Risk evaluation returned null");
                }

                // Step 3: Check if approved
                if (!riskDecision.isApproved()) {
                    String reason = tradeDecisionPipeline.getApprovalReason(riskDecision);
                    log.warn("ManualTradeController: Trade rejected - {}", reason);
                    throw new RuntimeException("Trade rejected: " + reason);
                }

                // Step 4: Execute approved trade
                log.info("ManualTradeController: Trade approved. Executing with size={}",
                        riskDecision.getFinalPositionSize());

                Trade order = tradingService.executeTrade(
                        exchange,
                        riskContext.getTradePair(),
                        riskContext.getCurrentPrice(),
                        quantity,
                        riskContext.getContractType(),
                        Side.valueOf(action),
                        riskContext.getStopLossPrice(),
                        riskContext.getTakeProfitPrice(),
                        riskContext.getSlippageImpact());

                log.info("ManualTradeController: Order placed: {}", order.getTimestamp());
                return order;

            } catch (Exception e) {
                log.error("ManualTradeController: Failed to execute {} {} {}",
                        action, quantity, symbol, e);
                throw new RuntimeException("Trade execution failed: " + e.getMessage(), e);
            }
        });
    }
}
