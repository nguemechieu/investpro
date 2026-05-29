package org.investpro.strategy.ai;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;
import org.investpro.decision.MarketRegime;
import org.investpro.event.EventBusManager;
import org.investpro.strategy.StrategySignal;
import org.investpro.strategy.lifecycle.AISignalDecision;
import org.investpro.strategy.lifecycle.AISignalReview;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reviews individual trading signals before they are routed through the execution pipeline.
 *
 * <p><strong>CRITICAL:</strong> This engine is an advisor/reviewer only.
 * AI NEVER places orders. The decision returned here is advisory input
 * to the ExecutionRouter. The ExecutionEngine makes all final trading decisions.</p>
 *
 * <p>AI may REJECT, APPROVE, REDUCE_SIZE, or WAIT on a signal—but the execution pipeline
 * remains in full control of whether to act on this guidance.</p>
 */
@Slf4j
public class AISignalReviewEngine {

    private static volatile AISignalReviewEngine instance;

    private final AtomicBoolean enabled;
    private final double minConfidence;

    private static final String SOURCE = "AISignalReviewEngine";

    private AISignalReviewEngine() {
        this.enabled = new AtomicBoolean(
                Boolean.parseBoolean(System.getProperty("ai.signal.review.enabled", "true")));
        this.minConfidence = Double.parseDouble(
                System.getProperty("ai.signal.minConfidence", "0.50"));
        log.info("AISignalReviewEngine initialised: enabled={}, minConfidence={}",
                enabled.get(), minConfidence);
    }

    /**
     * Returns the singleton instance.
     *
     * @return singleton AISignalReviewEngine
     */
    public static AISignalReviewEngine getInstance() {
        AISignalReviewEngine local = instance;
        if (local == null) {
            synchronized (AISignalReviewEngine.class) {
                local = instance;
                if (local == null) {
                    local = new AISignalReviewEngine();
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * Reviews a signal against current market conditions.
     *
     * <p>AI may review but NEVER places orders. Decision is advisory to ExecutionRouter.</p>
     *
     * @param signal        the trading signal to evaluate
     * @param currentRegime the current market regime
     * @return AISignalReview with advisory decision
     */
    public AISignalReview reviewSignal(StrategySignal signal, MarketRegime currentRegime) {
        if (signal == null) {
            log.warn("reviewSignal called with null signal");
            return buildLowConfidenceReview(null, null, null, null,
                    List.of("Signal was null"));
        }

        log.debug("Reviewing signal: strategy={} symbol={} side={} confidence={}",
                signal.getStrategyId(), signal.getSymbol(),
                signal.getSide(), signal.getConfidence());

        List<String> warnings = new ArrayList<>();

        // --- Confidence check ---
        if (signal.getConfidence() < minConfidence) {
            warnings.add(String.format("Signal confidence %.2f below minimum %.2f",
                    signal.getConfidence(), minConfidence));
        }

        // --- Volatility score (heuristic from risk distance) ---
        double volatilityScore = estimateVolatilityScore(signal);
        if (volatilityScore > 0.75) {
            warnings.add("High volatility detected - spread risk elevated");
        }

        // --- Liquidity score (heuristic: crypto pairs vs FX) ---
        double liquidityScore = estimateLiquidityScore(signal.getSymbol());

        // --- Spread score (inverse of risk distance normalised) ---
        double spreadScore = estimateSpreadScore(signal);

        // --- News risk (conservative heuristic, no external API) ---
        double newsRiskScore = 0.20; // low-moderate baseline

        // --- Regime compatibility ---
        double regimeCompatibility = computeRegimeCompatibility(signal, currentRegime);
        if (regimeCompatibility < 0.40) {
            warnings.add("Poor regime compatibility: regime=" + currentRegime
                    + " may not suit this signal direction");
        }

        // --- Market condition score ---
        double marketConditionScore = (liquidityScore * 0.3 + spreadScore * 0.2
                + regimeCompatibility * 0.3 + (1.0 - newsRiskScore) * 0.2);

        // --- Decision ---
        AISignalDecision decision;
        double aiConfidence;
        double sizeMultiplier = 1.0;
        String reasoning;

        if (signal.getConfidence() < minConfidence && !warnings.isEmpty()) {
            decision = AISignalDecision.LOW_CONFIDENCE;
            aiConfidence = 0.40;
            reasoning = "Signal confidence below threshold. " + String.join("; ", warnings);
        } else if (regimeCompatibility < 0.30) {
            decision = AISignalDecision.REJECT;
            aiConfidence = 0.70;
            reasoning = "Regime incompatibility too severe. Current regime: " + currentRegime;
        } else if (volatilityScore > 0.80) {
            decision = AISignalDecision.WAIT;
            aiConfidence = 0.65;
            reasoning = "Volatility too high for safe entry. Consider waiting for conditions to stabilise.";
        } else if (volatilityScore > 0.60 || regimeCompatibility < 0.50) {
            decision = AISignalDecision.REDUCE_SIZE;
            sizeMultiplier = 0.50;
            aiConfidence = 0.60;
            reasoning = "Moderate risk conditions. Recommend 50% position size to manage exposure.";
        } else if (marketConditionScore >= 0.65 && signal.getConfidence() >= minConfidence) {
            decision = AISignalDecision.APPROVE;
            aiConfidence = 0.70 + (signal.getConfidence() * 0.20);
            reasoning = String.format(
                    "Signal approved. MarketScore=%.2f, Confidence=%.2f, RegimeCompat=%.2f",
                    marketConditionScore, signal.getConfidence(), regimeCompatibility);
        } else {
            decision = AISignalDecision.LOW_CONFIDENCE;
            aiConfidence = 0.45;
            reasoning = "Conditions borderline. " + String.join("; ", warnings);
        }

        AISignalReview review = AISignalReview.builder()
                .reviewId(UUID.randomUUID().toString())
                .signalId(signal.getStrategyId() + "-" + Instant.now().toEpochMilli())
                .strategyId(signal.getStrategyId())
                .symbol(signal.getSymbol())
                .timeframe(signal.getTimeframe())
                .decision(decision)
                .aiConfidence(Math.min(aiConfidence, 0.95))
                .reasoningSummary(reasoning)
                .marketConditionScore(marketConditionScore)
                .volatilityScore(volatilityScore)
                .liquidityScore(liquidityScore)
                .spreadScore(spreadScore)
                .newsRiskScore(newsRiskScore)
                .regimeCompatibility(regimeCompatibility)
                .suggestedSizeMultiplier(sizeMultiplier)
                .warningFlags(warnings)
                .reviewedAt(Instant.now())
                .build();

        log.info("Signal review: strategy={} symbol={} decision={} confidence={}",
                signal.getStrategyId(), signal.getSymbol(), decision, String.format("%.2f", aiConfidence));

        EventBusManager.getInstance().publish(
                AgentEvent.of(AgentEvent.AI_SIGNAL_REVIEWED, SOURCE, review));

        return review;
    }

    /** @return whether the AI signal review engine is currently active. */
    public boolean isEnabled() {
        return enabled.get();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private double estimateVolatilityScore(StrategySignal signal) {
        if (signal.getEntryPrice() <= 0 || signal.getStopLossPrice() <= 0) {
            return 0.50;
        }
        double riskPct = Math.abs(signal.getEntryPrice() - signal.getStopLossPrice())
                / signal.getEntryPrice();
        return Math.min(riskPct * 20.0, 1.0); // normalise: 5% stop = score 1.0
    }

    private double estimateLiquidityScore(String symbol) {
        if (symbol == null) return 0.50;
        String upper = symbol.toUpperCase();
        if (upper.contains("BTC") || upper.contains("ETH")) return 0.90;
        if (upper.contains("EUR") || upper.contains("USD")) return 0.85;
        if (upper.contains("SOL") || upper.contains("BNB")) return 0.75;
        return 0.60;
    }

    private double estimateSpreadScore(StrategySignal signal) {
        if (signal.getEntryPrice() <= 0) return 0.60;
        // Tighter stop = tighter spread assumption
        double riskPct = Math.abs(signal.getEntryPrice() - signal.getStopLossPrice())
                / signal.getEntryPrice();
        return Math.max(1.0 - (riskPct * 10.0), 0.20);
    }

    private double computeRegimeCompatibility(StrategySignal signal, MarketRegime regime) {
        if (regime == null || signal.getSide() == null) return 0.50;
        return switch (regime) {
            case STRONG_UPTREND -> signal.isBuy() ? 0.90 : 0.20;
            case STRONG_DOWNTREND -> signal.isSell() ? 0.90 : 0.20;
            case WEAK_UPTREND -> signal.isBuy() ? 0.70 : 0.40;
            case WEAK_DOWNTREND -> signal.isSell() ? 0.70 : 0.40;
            case RANGE_BOUND -> 0.60;
            case HIGH_VOLATILITY -> 0.30;
            case LOW_VOLATILITY -> 0.65;
            case TRANSITIONAL -> 0.40;
            case UNKNOWN -> 0.45;
        };
    }

    private AISignalReview buildLowConfidenceReview(String signalId, String strategyId,
                                                    String symbol, String timeframe,
                                                    List<String> warnings) {
        return AISignalReview.builder()
                .reviewId(UUID.randomUUID().toString())
                .signalId(signalId != null ? signalId : "UNKNOWN")
                .strategyId(strategyId != null ? strategyId : "UNKNOWN")
                .symbol(symbol != null ? symbol : "UNKNOWN")
                .timeframe(timeframe != null ? timeframe : "UNKNOWN")
                .decision(AISignalDecision.LOW_CONFIDENCE)
                .aiConfidence(0.10)
                .reasoningSummary("Unable to review: " + String.join("; ", warnings))
                .suggestedSizeMultiplier(0.0)
                .warningFlags(warnings)
                .reviewedAt(Instant.now())
                .build();
    }
}
