package org.investpro.ai;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.investpro.models.trading.TradePair;
import org.investpro.risk.RiskDecision;
import org.investpro.risk.TradeRiskContext;
import org.investpro.utils.Side;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;

/**
 * Immutable request object passed to AiReasoningService for trade review.
 * Contains all context needed for AI to make an informed, risk-aware decision.
 * <p>
 * The AI uses this to analyze the trade setup without modifying any state.
 */
@Slf4j
@Value
@Builder
public class AiTradeReviewRequest {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AiTradeReviewRequest.class);
    // =========================================================================
    // Market & Signal Context
    // =========================================================================

    /** Trading pair symbol (e.g., "BTC/USD") */
    TradePair symbol;

    /** Asset class (CRYPTO, FOREX, STOCK, COMMODITY) */
    String assetClass;

    /** Contract type (SPOT, FUTURES, OPTIONS, PERPETUAL) */
    String contractType;

    /** Broker/exchange name */
    String broker;

    /** Trading side: LONG or SHORT */
    String signalSide;

    /** Signal confidence 0.0-1.0 (0.5 = moderate, 0.9 = high) */
    double signalConfidence;

    /** Strategy name that generated the signal */
    String strategyName;

    /**
     * Human-readable reason for the signal (e.g., "Stochastic K crossed below D in
     * overbought")
     */
    String signalReason;

    // =========================================================================
    // Risk Framework Context
    // =========================================================================

    /** TradeRiskContext with all dimensions (profiles, analysis) */
    TradeRiskContext riskContext;

    /** RiskDecision from RiskManagementSystem (guardrails) */
    RiskDecision riskDecision;

    // =========================================================================
    // Market Data
    // =========================================================================

    /** Current market price */
    double currentPrice;

    /** Bid/ask spread percentage */
    double spreadPercent;

    /** Average True Range (volatility indicator) */
    double atr;

    /** 20-period volatility (standard deviation of returns) */
    double volatilityPercent;

    // =========================================================================
    // Account State
    // =========================================================================

    /** Account equity in base currency */
    double accountEquity;

    /** Current drawdown percentage (0.0 = no drawdown, 20.0 = 20% down) */
    double currentDrawdownPercent;

    /** Current portfolio heat percentage (total risk exposure) */
    double portfolioHeatPercent;

    // =========================================================================
    // Position Summary
    // =========================================================================

    /** Summary of currently open positions (count, symbols, exposures) */
    @Nullable
    String openPositionsSummary;

    /** Summary of recent trades (win rate, recent P&L, streak) */
    @Nullable
    String recentTradeHistorySummary;

    // =========================================================================
    // Optional Context
    // =========================================================================

    /** Optional news or fundamental context (e.g., "Fed decision today") */
    @Nullable
    String newsContext;

    /** Optional trading plan notes from user */
    @Nullable
    String userNotes;

    /** Timestamp when request was created */
    LocalDateTime createdAt;

    public static AiTradeReviewRequest from(
            Side signal,
            TradeRiskContext riskContext,
            RiskDecision riskDecision) {
        if (signal == null) {
            throw new IllegalArgumentException("signal cannot be null");
        }

        if (riskContext == null) {
            throw new IllegalArgumentException("riskContext cannot be null");
        }

        String signalReason = "No signal reason provided";

        return AiTradeReviewRequest.builder()
                // Market & signal context
                .signalSide(String.valueOf(signal))
                .signalConfidence(0.5)
                .strategyName("UNKNOWN")
                .signalReason(signalReason)

                // Risk framework context
                .riskContext(riskContext)
                .riskDecision(riskDecision)

                // Market data
                .currentPrice(resolveCurrentPrice(signal, riskContext))
                .spreadPercent(resolveSpreadPercent(riskContext))
                .atr(resolveAtr(riskContext))
                .volatilityPercent(resolveVolatilityPercent(riskContext))

                // Account state
                .accountEquity(riskContext.getAccountEquity())
                .currentDrawdownPercent(resolveCurrentDrawdownPercent(riskContext))
                .portfolioHeatPercent(
                        riskDecision != null
                                ? riskDecision.getPortfolioHeat()
                                : resolvePortfolioHeatPercent(riskContext))

                // Position / trade history summaries
                .openPositionsSummary(null)
                .recentTradeHistorySummary(null)

                // Optional context
                .newsContext(null)
                .userNotes(null)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private static double resolveCurrentPrice(
            Side signal,
            TradeRiskContext riskContext) {
        if (signal != null && riskContext != null && riskContext.getEntryPrice() > 0) {
            return riskContext.getEntryPrice();
        }

        if (riskContext != null && riskContext.getEntryPrice() > 0) {
            return riskContext.getEntryPrice();
        }

        return 0.0;
    }

    private static double resolveSpreadPercent(TradeRiskContext riskContext) {
        /*
         * If TradeRiskContext has a real spread field, replace this with:
         * return riskContext.getSpreadPercent();
         */
        return riskContext.getSpreadPercent();
    }

    private static double resolveAtr(TradeRiskContext riskContext) {
        /*
         * If TradeRiskContext has ATR, replace this with:
         * return riskContext.getAtr();
         */
        return riskContext.getAtr();
    }

    private static double resolveVolatilityPercent(TradeRiskContext riskContext) {
        /*
         * If TradeRiskContext has volatility, replace this with:
         * return riskContext.getVolatilityPercent();
         */
        return riskContext.getVolatilityPercent();
    }

    private static double resolveCurrentDrawdownPercent(TradeRiskContext riskContext) {
        // If TradeRiskContext has drawdown, replace this with:
        return riskContext.getCurrentDrawdownPercent();

    }

    private static double resolvePortfolioHeatPercent(TradeRiskContext riskContext) {

        return riskContext.getPortfolioHeatPercent();

    }
}
