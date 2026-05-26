package org.investpro.decision;

import org.investpro.market.MarketContext;
import org.investpro.strategy.signals.SignalDecision;
import org.investpro.strategy.signals.TradingAction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BotDecisionEngine {
    private static final BigDecimal MIN_CONFIDENCE = new BigDecimal("0.65");
    private static final BigDecimal MAX_SPREAD = new BigDecimal("0.02");

    public BotTradeDecision review(SignalDecision signalDecision, MarketContext context) {
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (signalDecision == null || !signalDecision.actionable()) {
            blockers.add("Aggregated signal is not actionable.");
        }
        if (signalDecision != null && signalDecision.finalConfidence().compareTo(MIN_CONFIDENCE) < 0) {
            blockers.add("Signal confidence is below bot threshold.");
        }
        if (context == null) {
            blockers.add("Market context is unavailable.");
        } else {
            if (context.dataFreshnessStatus() != MarketContext.DataFreshnessStatus.FRESH) {
                blockers.add("Market data is stale or unavailable.");
            }
            if (context.spread().compareTo(MAX_SPREAD) > 0) {
                blockers.add("Spread is above deterministic bot threshold.");
            }
            if (context.hasPendingOrder()) {
                blockers.add("A pending order already exists for this symbol.");
            }
            if (context.exchangeCapabilities().degraded() || !context.exchangeCapabilities().connected()) {
                blockers.add("Exchange is disconnected or degraded.");
            }
            if (context.hasOpenPosition()) {
                warnings.add("An open position already exists for this symbol.");
            }
        }

        boolean allowed = blockers.isEmpty();
        DecisionAction action = allowed && signalDecision != null && signalDecision.finalAction() != TradingAction.HOLD
                ? DecisionAction.TRADE
                : DecisionAction.HOLD;
        if (!allowed) {
            action = DecisionAction.SKIP;
        }

        return new BotTradeDecision(null, action, allowed,
                signalDecision == null ? BigDecimal.ZERO : signalDecision.finalConfidence(),
                allowed ? "Bot deterministic review approved the setup." : String.join("; ", blockers),
                signalDecision, blockers, warnings, Instant.now(), Map.of("review", "deterministic-phase1"));
    }
}
