package org.investpro.ai;

import lombok.Builder;
import lombok.Value;
import org.investpro.risk.RiskDecision;
import org.investpro.risk.TradeRiskContext;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;


/**
 * Immutable request object passed to AiReasoningService for trade review.
 * Contains all context needed for AI to make an informed, risk-aware decision.
 *
 * The AI uses this to analyze the trade setup without modifying any state.
 */
@Value
@Builder
public class AiTradeReviewRequest {
    // =========================================================================
    // Market & Signal Context
    // =========================================================================
    
    /** Trading pair symbol (e.g., "BTC/USD") */
    String symbol;
    
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
    
    /** Human-readable reason for the signal (e.g., "Stochastic K crossed below D in overbought") */
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
}
