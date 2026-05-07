package org.investpro.strategy;

/**
 * DOCUMENTATION-ONLY CLASS
 *
 * This class serves as a marker for the strategy integration guide.
 * The actual comprehensive guide has been moved to:
 * 
 * <pre>
 * docs / strategy / strategy - integration - guide.md
 * </pre>
 *
 * Key integration points:
 * <ul>
 * <li>Strategies are initialized once via StrategyBootstrapper.initialize() at
 * startup</li>
 * <li>Signals are generated via StrategyDecisionService.generateDecision()</li>
 * <li>All signals pass through RiskManagementSystem before execution</li>
 * <li>UI requests data but does not make trading decisions</li>
 * <li>Core engine handles: strategy selection → signal generation → risk
 * evaluation → execution</li>
 * </ul>
 *
 * For detailed implementation guide, see
 * docs/strategy/strategy-integration-guide.md
 */
public final class StrategyIntegrationGuide {
    private StrategyIntegrationGuide() {
        // This is a documentation-only class. Do not instantiate.
    }
}
// *
// * The InvestPro trading system now supports multi-strategy management with
// automatic
// * strategy selection, backtesting, and AI-assisted decision making.
// *
// *
// ============================================================================
// * QUICK START: Enable Strategy Selection in Your Trading Bot
// *
// ============================================================================
// *
// * 1. Initialize Strategies at Startup
// * -----------------------------------
// * Call this ONCE during application startup:
// *
// * StrategyInitializer.initializeStrategies();
// *
// * This registers all available strategies (TrendFollowing, MeanReversion,
// Breakout)
// * into the StrategyRegistry singleton.
// *
// * Recommended location: SmartBot.start() method or main() entry point
// *
// *
// * 2. Select Strategy for Symbol/Timeframe
// * ----------------------------------------
// * When trading a symbol, first assign the best strategy:
// *
// * StrategySelectionService selectionService =
// StrategySelectionService.getInstance();
// * StrategyAssignment assignment =
// selectionService.getCurrentAssignment(symbol, timeframe);
// *
// * if (assignment != null && assignment.isValid()) {
// * // Use assigned strategy for this symbol/timeframe
// * String strategyId = assignment.getStrategyId();
// * TradingStrategy strategy =
// StrategyRegistry.getInstance().getStrategy(strategyId);
// * }
// *
// *
// * 3. Generate Signal from Assigned Strategy
// * ------------------------------------------
// * Build a StrategyContext with market data:
// *
// * StrategyContext context = StrategyContext.builder()
// * .symbol(symbol)
// * .timeframe(timeframe)
// * .candles(historicalCandles) // List<CandleData>, oldest to newest
// * .currentPrice(currentPrice)
// * .bid(bidPrice)
// * .ask(askPrice)
// * .volatility(calculatedVolatility)
// * .averageVolume(avgVolume)
// * .timestamp(Instant.now())
// * .build();
// *
// * StrategySignal signal = strategy.generateSignal(context);
// *
// * The signal contains:
// * - side: BUY, SELL, or HOLD
// * - confidence: 0.0-1.0
// * - entryPrice, stopLoss, takeProfit
// * - riskRewardRatio
// * - reasons: List<String> explaining the signal
// * - warnings: List<String> flagging potential issues
// *
// *
// * 4. Manual Strategy Override (Optional)
// * ----------------------------------------
// * Force a specific strategy for a symbol/timeframe:
// *
// * selectionService.manuallyAssign(symbol, timeframe, "trend-following",
// locked=true, reason);
// *
// * locked=true prevents the system from auto-replacing this assignment.
// * locked=false allows auto-replacement when better strategies emerge.
// *
// *
// * 5. Disable Strategy (Optional)
// * --------------------------------
// * Temporarily disable a strategy for a symbol:
// *
// * selectionService.disableStrategy(symbol, timeframe, "Strategy
// underperforming");
// *
// *
// *
// ============================================================================
// * AVAILABLE STRATEGIES
// *
// ============================================================================
// *
// * 1. TREND_FOLLOWING ("trend-following")
// * - Approach: SMA20/SMA50 crossover
// * - Timeframes: H1, H4, D1, W1 (long-term trends)
// * - Assets: CRYPTO, FOREX, EQUITY on SPOT, PERPETUAL
// * - Confidence: 0.65
// * - Best For: Sustained uptrends/downtrends
// *
// * 2. MEAN_REVERSION ("mean-reversion")
// * - Approach: Bollinger Bands (20, 2std) + RSI(14)
// * - Timeframes: M15, M30, H1, H4 (short to medium-term reversions)
// * - Assets: CRYPTO, FOREX, EQUITY on SPOT
// * - Confidence: 0.60
// * - Best For: Range-bound markets with extremes
// *
// * 3. BREAKOUT ("breakout")
// * - Approach: Donchian Channel (20-period)
// * - Timeframes: M5, M15, H1, H4, D1
// * - Assets: CRYPTO, FOREX, COMMODITY on SPOT, PERPETUAL
// * - Confidence: 0.68
// * - Best For: Volatility breakouts
// *
// *
// *
// ============================================================================
// * BACKTESTING & STRATEGY RANKING
// *
// ============================================================================
// *
// * After backtesting strategies, rank them using StrategyRankingEngine:
// *
// * StrategyRankingEngine rankingEngine = new StrategyRankingEngine();
// * StrategyScore score = rankingEngine.scoreStrategy(backtestResult);
// *
// * The score includes:
// * - totalScore (0-100): Final weighted score
// * - profitabilityScore (35% weight): Return, Profit Factor, Expectancy
// * - riskScore (25% weight): Drawdown, Sharpe Ratio, Max Loss
// * - consistencyScore (20% weight): Win Rate, Consecutive Losses
// * - executionScore (10% weight): Trade Count, Fees, Slippage
// * - stabilityScore (10% weight): Calmar Ratio, Positive Returns
// * - overfittingPenalty (-0 to -50): Suspicious backtest results
// *
// * Quality thresholds:
// * - isHighQuality(): score >= 70 AND stability >= 65 AND overfit > -20
// * - isAcceptable(): score >= 55 AND risk >= 50
// * - hasRedFlags(): score < 50 OR stability < 40 OR risk < 30
// *
// *
// * Store backtest results for later reference:
// *
// * selectionService.storeBacktestResult(backtestResult);
// * List<StrategyBacktestResult> results =
// selectionService.getBacktestResults(symbol, timeframe);
// *
// *
// *
// ============================================================================
// * ASSIGNMENT HISTORY & AUDIT TRAIL
// *
// ============================================================================
// *
// * Track all strategy changes for audit:
// *
// * StrategySelectionService.StrategyAssignmentHistory history =
// * selectionService.getAssignmentHistory(symbol, timeframe);
// *
// * for (HistoryEntry entry : history.getEntries()) {
// * System.out.println(entry.strategyId() + " at " + entry.timestamp() + ": " +
// entry.note());
// * }
// *
// *
// *
// ============================================================================
// * ARCHITECTURE
// *
// ============================================================================
// *
// * TradingStrategy (Interface)
// * ├── BaseStrategy (Abstract)
// * │ ├── TrendFollowingStrategy
// * │ ├── MeanReversionStrategy
// * │ └── BreakoutStrategy
// * └── [Custom strategies can be added]
// *
// * StrategyRegistry (Singleton)
// * └── Stores and queries all registered strategies
// *
// * StrategyAssignment
// * └── Represents which strategy is assigned to symbol/timeframe
// * (with modes: AUTO, MANUAL, AI_ASSISTED, DISABLED)
// *
// * StrategyAssignmentRepository (Singleton)
// * └── Persists and queries assignments
// *
// * StrategySelectionService (Singleton)
// * ├── selectAndAssign(): Auto-select best strategy from backtest results
// * ├── manuallyAssign(): User override with optional lock
// * ├── disableStrategy(): Disable strategy for symbol/timeframe
// * ├── getCurrentAssignment(): Query active assignment
// * └── getAssignmentHistory(): Audit trail
// *
// * StrategyBacktestResult
// * └── Metrics from backtesting (trades, win rate, drawdown, Sharpe ratio,
// etc.)
// *
// * StrategyRankingEngine
// * └── Scores strategies using multi-factor risk-adjusted model
// *
// * StrategyScore
// * └── Result of ranking (0-100 total score + component scores)
// *
// *
// *
// ============================================================================
// * SAFETY GUARANTEES
// *
// ============================================================================
// *
// * 1. Locked Assignments Cannot Be Auto-Replaced
// * - Manual assignments with locked=true prevent auto-selection
// * - System respects user override decisions
// *
// * 2. All Strategies Return Normalized Signals
// * - Fair comparison across different strategy types
// * - Confidence scores ensure consistent interpretation
// *
// * 3. Quality Gate on Auto-Selection
// * - Only assigns strategies that pass quality thresholds
// * - Rejects strategies with overfitting indicators
// *
// * 4. Assignment History Tracked
// * - Every change recorded with timestamp and reason
// * - Full audit trail for production compliance
// *
// * 5. Disabled State Explicit
// * - No signal generation when disabled
// * - Clear reason why strategy is not used
// *
// *
// *
// ============================================================================
// * EXAMPLE: COMPLETE WORKFLOW
// *
// ============================================================================
// *
// * // 1. Initialize strategies at startup
// * StrategyInitializer.initializeStrategies();
// *
// * // 2. Run backtests (in your backtesting service)
// * List<StrategyBacktestResult> results = runBacktestForSymbol(symbol,
// timeframe);
// *
// * // 3. Rank strategies
// * StrategyRankingEngine rankingEngine = new StrategyRankingEngine();
// * List<StrategyScore> rankedStrategies =
// rankingEngine.rankStrategies(results);
// *
// * // 4. Auto-assign best strategy
// * StrategySelectionService selectionService =
// StrategySelectionService.getInstance();
// * StrategyAssignment assignment = selectionService.selectAndAssign(symbol,
// timeframe, results);
// *
// * // 5. Later, in live trading, get the assignment
// * StrategyAssignment current = selectionService.getCurrentAssignment(symbol,
// timeframe);
// * if (current != null) {
// * TradingStrategy strategy =
// StrategyRegistry.getInstance().getStrategy(current.getStrategyId());
// * StrategySignal signal = strategy.generateSignal(context);
// * // Use signal...
// * }
// *
// * // 6. Manual override if needed
// * selectionService.manuallyAssign(symbol, timeframe, "mean-reversion", true,
// "User expertise");
// *
// *
// *
// ============================================================================
// * NEXT STEPS
// *
// ============================================================================
// *
// * 1. ✅ Strategies registered and available
// * 2. ⏳ Integrate StrategyInitializer.initializeStrategies() into SmartBot
// * 3. ⏳ Build backtesting matrix runner for all symbol/timeframe/strategy
// combos
// * 4. ⏳ Integrate ranking engine with backtesting results
// * 5. ⏳ Add UI panels for research, assignment, and performance monitoring
// * 6. ⏳ Optional: Add AI strategy selection layer (OpenAI review)
// * 7. ⏳ Optional: Add strategy drift detector for live vs backtest comparison
// *
// */
// public class StrategyIntegrationGuide {
// // This is a documentation class - no runtime code
// }
