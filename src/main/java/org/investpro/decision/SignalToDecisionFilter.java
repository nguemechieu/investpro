package org.investpro.decision;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.execution.TradeExecutionCoordinator;
import org.investpro.models.trading.Ticker;
import org.investpro.risk.TradeRiskContext;
import org.investpro.strategy.StrategySignal;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Signal filter that intercepts all signals and converts them to
 * BotTradeDecisions.
 * 
 * The bot NEVER executes signals directly.
 * Every signal is validated and scored through the BotTradeDecisionEngine
 * before execution.
 * 
 * This class sits between the strategy engine and the trade execution
 * coordinator.
 */
@Slf4j
@RequiredArgsConstructor
public class SignalToDecisionFilter {

    private final BotTradeDecisionEngine decisionEngine;
    private final TradeExecutionCoordinator executionCoordinator;

    /**
     * Evaluate signal and determine if trading should proceed.
     * 
     * @param signal      Strategy signal to evaluate
     * @param riskContext Risk management context
     * @param ticker      Current market data
     * @return true if decision engine approves the trade, false otherwise
     */
    public boolean shouldExecuteSignal(
            @NotNull StrategySignal signal,
            @NotNull TradeRiskContext riskContext,
            @NotNull Ticker ticker) {

        Objects.requireNonNull(signal, "signal cannot be null");
        Objects.requireNonNull(riskContext, "riskContext cannot be null");
        Objects.requireNonNull(ticker, "ticker cannot be null");

        try {
            // === Convert signal to decision ===
            BotTradeDecision decision = decisionEngine.evaluateSignal(
                    ticker.getTradePair(),
                    signal.getSide(),
                    ticker,
                    signal.getConfidence());

            log.info("Decision engine output: {}", decision.getDecisionSummary());

            // Log full analysis details
            if (!decision.fullAnalysisSummary().isBlank()) {
                log.debug("Full analysis:\n{}", decision.fullAnalysisSummary());
            }

            // Check for blocking issues
            if (decision.hasBlockingIssues()) {
                String blockerMsg = String.join("; ", decision.blockers());
                log.warn("Trade SKIPPED - Blockers: {}", blockerMsg);
                return false;
            }

            // Check final decision
            if (decision.willSkip()) {
                String reason = String.join("; ", decision.reasons());
                log.info("Trade SKIPPED - Reason: {}", reason);
                return false;
            }

            // Decision is TRADE - approved
            log.info("Decision engine APPROVED {} {} | EV: {} | Setup: {}",
                    signal.getSide().name(),
                    ticker.getTradePair(),
                    decision.expectation().getExpectedValueFormatted(),
                    decision.getSetupSourceDescription());

            return true;

        } catch (Exception e) {
            log.error("Error in decision engine evaluation", e);
            return false;
        }
    }
}
