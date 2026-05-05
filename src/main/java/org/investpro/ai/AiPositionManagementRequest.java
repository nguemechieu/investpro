package org.investpro.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.investpro.models.trading.TradePair;
import org.investpro.risk.CapitalProtection;
import org.investpro.risk.LiquidityProfile;
import org.investpro.risk.MarketBehavior;
import org.investpro.risk.PsychologyProfile;
import org.investpro.risk.RiskProfile;

/**
 * Request object sent to AI position manager for review of an open position.
 * Contains all relevant position state, market data, risk metrics, and context.
 *
 * AI must use this context to recommend position actions (hold, reduce, close, etc.)
 * but cannot directly execute orders—recommendations feed through RiskManagementSystem and FinalRiskGate.
 */
@Builder
@Getter
@Setter
@AllArgsConstructor
public class AiPositionManagementRequest {
    
    // =========================================================================
    // Position Identity
    // =========================================================================
    
    /** Unique position ID within the trading system */
    private String positionId;
    
    /** Trading pair (e.g., BTC/USD) */
    private TradePair symbol;
    
    /** Asset class (CRYPTO, FOREX, STOCK, COMMODITY, INDEX) */
    private String assetClass;
    
    /** Contract type (SPOT, PERPETUAL, FUTURES, OPTIONS) */
    private String contractType;
    
    /** Broker/exchange name */
    private String broker;
    
    // =========================================================================
    // Position State
    // =========================================================================
    
    /** Position side: BUY (long) or SELL (short) */
    private String side;
    
    /** Price at which position was entered */
    private double entryPrice;
    
    /** Current market price */
    private double currentPrice;
    
    /** Position quantity (always positive, side determines direction) */
    private double quantity;
    
    /** Leverage used (1.0 = no leverage) */
    private double leverage;
    
    // =========================================================================
    // P&L and Risk Metrics
    // =========================================================================
    
    /** Unrealized profit/loss in currency */
    private double unrealizedPnl;
    
    /** Unrealized P&L as percentage of position notional value */
    private double unrealizedPnlPercent;
    
    /** Maximum favorable excursion (best price since entry) */
    private double maxFavorableExcursion;
    
    /** Maximum adverse excursion (worst price since entry) */
    private double maxAdverseExcursion;
    
    /** Current stop-loss price (null if not set) */
    private Double currentStopLoss;
    
    /** Current take-profit price (null if not set) */
    private Double currentTakeProfit;
    
    /** Liquidation price (for leveraged positions, null otherwise) */
    private Double liquidationPrice;
    
    // =========================================================================
    // Market Microstructure
    // =========================================================================
    
    /** Average True Range (volatility measure) */
    private double atr;
    
    /** Market volatility (annualized percentage) */
    private double volatility;
    
    /** Bid-ask spread in basis points */
    private double spread;
    
    // =========================================================================
    // Market & Account Context
    // =========================================================================
    
    /** Current market behavior analysis */
    private MarketBehavior marketBehavior;
    
    /** Liquidity profile for the symbol */
    private LiquidityProfile liquidityProfile;
    
    /** Account risk profile (conservative, moderate, aggressive) */
    private RiskProfile riskProfile;
    
    /** Capital protection rules in effect */
    private CapitalProtection capitalProtection;
    
    /** Psychology profile (sentiment, confidence, bias factors) */
    private PsychologyProfile psychologyProfile;
    
    // =========================================================================
    // Account State
    // =========================================================================
    
    /** Total account equity */
    private double accountEquity;
    
    /** Current portfolio drawdown percentage */
    private double currentDrawdown;
    
    /** Portfolio heat: sum of all position risks as % of equity */
    private double portfolioHeat;
    
    // =========================================================================
    // Position History & Context
    // =========================================================================
    
    /** Original trade thesis (reason for entering) */
    private String originalTradeThesis;
    
    /** Original entry reasons (list of factors that justified entry) */
    private java.util.List<String> originalEntryReasons;
    
    /** New warnings or changes since entry */
    private java.util.List<String> newWarnings;
    
    /** Recent market events that may affect position */
    private java.util.List<String> recentMarketEvents;
    
    /** Position age in minutes */
    private long positionAgeMinutes;
    
    /** Number of bars/candles since position entry */
    private int barsSinceEntry;
    
    // =========================================================================
    // Validation
    // =========================================================================
    
    /**
     * Validate that this request has all required fields set.
     * @return true if all critical fields are populated
     */
    public boolean isValid() {
        return positionId != null && !positionId.isBlank()
                && symbol != null
                && side != null
                && quantity > 0
                && Double.isFinite(entryPrice)
                && Double.isFinite(currentPrice)
                && Double.isFinite(accountEquity)
                && marketBehavior != null
                && riskProfile != null;
    }
    
    /**
     * Get a summary of the position for logging/display.
     */
    public String getSummary() {
        return String.format("%s %s x%.2f @ %.2f (Entry: %.2f, P&L: %+.2f%%)",
                side,
                symbol != null ? symbol.toString('/') : "N/A",
                quantity,
                currentPrice,
                entryPrice,
                unrealizedPnlPercent);
    }
}
