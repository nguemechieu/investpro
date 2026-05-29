package org.investpro.strategy.nocode;

import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.investpro.strategy.StrategyContext;
import org.investpro.strategy.StrategySignal;

import java.util.List;

/**
 * Evaluates a compiled no-code strategy definition against a
 * {@link StrategyContext} and produces a {@link StrategySignal}.
 *
 * <p>The runtime steps are:
 * <ol>
 *   <li>Evaluate <em>entry</em> rules in order. Emit the first matching rule's action.</li>
 *   <li>If no entry rule fires, evaluate <em>exit</em> rules in order.</li>
 *   <li>If no rule fires, emit HOLD.</li>
 * </ol>
 * </p>
 *
 * <p><strong>CRITICAL:</strong> This runtime only produces a {@link StrategySignal}.
 * It never submits an order or bypasses the RiskEngine / ExecutionEngine.</p>
 *
 * <p>Indicator calculations are performed using the candle list from
 * {@link StrategyContext#getCandles()}. The last element is the most recent bar.</p>
 *
 * <p>This class is stateless with respect to strategy state and is thread-safe
 * when using separate instances per evaluation context.</p>
 */
@Slf4j
public class NoCodeStrategyRuntime {

    private static final double EPSILON = 1e-9;

    private final CompiledNoCodeStrategy compiled;

    /**
     * Creates a runtime for the given compiled strategy.
     *
     * @param compiled the compiled strategy (must not be null)
     */
    public NoCodeStrategyRuntime(CompiledNoCodeStrategy compiled) {
        if (compiled == null) throw new IllegalArgumentException("Compiled strategy must not be null");
        this.compiled = compiled;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Evaluates the strategy against the provided context.
     *
     * @param ctx the current market context
     * @return a {@link StrategySignal} (BUY / SELL / HOLD)
     */
    public StrategySignal evaluate(StrategyContext ctx) {
        NoCodeStrategyDefinition def = compiled.getDefinition();
        List<CandleData> candles = ctx.getCandles();

        String sym = ctx.getSymbol() != null ? ctx.getSymbol().toString() : "UNKNOWN";
        String tf = ctx.getTimeframe() != null ? ctx.getTimeframe().toString() : "UNKNOWN";
        String stratId = def.getStrategyId();

        if (candles == null || candles.size() < compiled.getWarmupBars()) {
            log.debug("Insufficient candles ({} < {}); emitting HOLD",
                    candles == null ? 0 : candles.size(), compiled.getWarmupBars());
            return StrategySignal.hold(sym, tf, stratId, "Insufficient data for warmup");
        }

        // Evaluate entry rules first
        for (NoCodeRule rule : def.getEntryRules()) {
            if (evaluateRule(rule, candles)) {
                return toSignal(rule.getAction(), rule.getConfidence(), sym, tf, stratId,
                        rule.toPreviewText(), ctx.getCurrentPrice(), def.getRiskSettings());
            }
        }

        // Evaluate exit rules
        for (NoCodeRule rule : def.getExitRules()) {
            if (evaluateRule(rule, candles)) {
                return toSignal(rule.getAction(), rule.getConfidence(), sym, tf, stratId,
                        rule.toPreviewText(), ctx.getCurrentPrice(), def.getRiskSettings());
            }
        }

        return StrategySignal.hold(sym, tf, stratId, "No rule fired");
    }

    /** @return the compiled strategy backing this runtime. */
    public CompiledNoCodeStrategy getCompiled() {
        return compiled;
    }

    // =========================================================================
    // Rule evaluation
    // =========================================================================

    private boolean evaluateRule(NoCodeRule rule, List<CandleData> candles) {
        List<NoCodeCondition> conditions = rule.getConditions();
        if (conditions == null || conditions.isEmpty()) return false;

        if (rule.getLogicOperator() == NoCodeLogicOperator.AND) {
            for (NoCodeCondition cond : conditions) {
                if (!evaluateCondition(cond, candles)) return false;
            }
            return true;
        } else { // OR
            for (NoCodeCondition cond : conditions) {
                if (evaluateCondition(cond, candles)) return true;
            }
            return false;
        }
    }

    private boolean evaluateCondition(NoCodeCondition cond, List<CandleData> candles) {
        try {
            double leftValue = computeIndicator(cond.getLeftIndicator(), candles, false);
            NoCodeConditionOperator op = cond.getOperator();

            if (op.requiresPreviousBar()) {
                double leftPrev = computeIndicator(cond.getLeftIndicator(), candles, true);
                double rightCurrent = resolveRight(cond, candles, false);
                double rightPrev = resolveRight(cond, candles, true);
                return switch (op) {
                    case CROSSES_ABOVE -> leftPrev <= rightPrev && leftValue > rightCurrent;
                    case CROSSES_BELOW -> leftPrev >= rightPrev && leftValue < rightCurrent;
                    default -> false;
                };
            }

            double rightValue = resolveRight(cond, candles, false);

            return switch (op) {
                case GREATER_THAN -> leftValue > rightValue;
                case LESS_THAN -> leftValue < rightValue;
                case EQUALS -> Math.abs(leftValue - rightValue) < EPSILON;
                case GREATER_THAN_OR_EQUAL -> leftValue >= rightValue - EPSILON;
                case LESS_THAN_OR_EQUAL -> leftValue <= rightValue + EPSILON;
                case BETWEEN -> {
                    double lo = cond.getRightValue();
                    double hi = cond.getRightValue2();
                    yield !Double.isNaN(lo) && !Double.isNaN(hi) && leftValue >= lo && leftValue <= hi;
                }
                default -> false;
            };
        } catch (Exception e) {
            log.warn("Error evaluating condition '{}': {}", cond.toPreviewText(), e.getMessage());
            return false;
        }
    }

    private double resolveRight(NoCodeCondition cond, List<CandleData> candles, boolean usePrevious) {
        if (cond.hasIndicatorRight()) {
            return computeIndicator(cond.getRightIndicator(), candles, usePrevious);
        }
        return cond.getRightValue();
    }

    // =========================================================================
    // Indicator dispatch
    // =========================================================================

    private double computeIndicator(NoCodeIndicatorReference ref,
                                    List<CandleData> candles,
                                    boolean usePrevious) {
        if (ref == null) return Double.NaN;
        int offset = usePrevious ? 1 : 0;
        return switch (ref.getType()) {
            case PRICE_CLOSE -> getClose(candles, offset);
            case PRICE_OPEN  -> getOpen(candles, offset);
            case PRICE_HIGH  -> getHigh(candles, offset);
            case PRICE_LOW   -> getLow(candles, offset);
            case VOLUME      -> getVolume(candles, offset);
            case SMA         -> sma(candles, ref.effectivePeriod(), offset);
            case EMA         -> ema(candles, ref.effectivePeriod(), offset);
            case RSI         -> rsi(candles, ref.effectivePeriod(), offset);
            case MACD        -> macdLine(candles, offset);
            case MACD_SIGNAL -> macdSignalLine(candles, offset);
            case BOLLINGER_UPPER -> bollingerUpper(candles, ref.effectivePeriod(), offset);
            case BOLLINGER_LOWER -> bollingerLower(candles, ref.effectivePeriod(), offset);
            case BOLLINGER_MID   -> sma(candles, ref.effectivePeriod(), offset);
            case ATR         -> atr(candles, ref.effectivePeriod(), offset);
            case VOLUME_MA   -> volumeMa(candles, ref.effectivePeriod(), offset);
        };
    }

    // =========================================================================
    // Candle accessors (CandleData is a record: closePrice(), openPrice()...)
    // =========================================================================

    private double getClose(List<CandleData> c, int offset) {
        int i = c.size() - 1 - offset; return i >= 0 ? c.get(i).closePrice() : Double.NaN;
    }
    private double getOpen(List<CandleData> c, int offset) {
        int i = c.size() - 1 - offset; return i >= 0 ? c.get(i).openPrice() : Double.NaN;
    }
    private double getHigh(List<CandleData> c, int offset) {
        int i = c.size() - 1 - offset; return i >= 0 ? c.get(i).highPrice() : Double.NaN;
    }
    private double getLow(List<CandleData> c, int offset) {
        int i = c.size() - 1 - offset; return i >= 0 ? c.get(i).lowPrice() : Double.NaN;
    }
    private double getVolume(List<CandleData> c, int offset) {
        int i = c.size() - 1 - offset; return i >= 0 ? c.get(i).volume() : Double.NaN;
    }

    // =========================================================================
    // Moving averages
    // =========================================================================

    private double sma(List<CandleData> candles, int period, int offset) {
        int end = candles.size() - offset;
        int start = end - period;
        if (start < 0 || period <= 0) return Double.NaN;
        double sum = 0;
        for (int i = start; i < end; i++) sum += candles.get(i).closePrice();
        return sum / period;
    }

    private double ema(List<CandleData> candles, int period, int offset) {
        int end = candles.size() - offset;
        if (end < period || period <= 0) return Double.NaN;
        double k = 2.0 / (period + 1);
        double emaVal = 0;
        for (int i = 0; i < period; i++) emaVal += candles.get(i).closePrice();
        emaVal /= period;
        for (int i = period; i < end; i++) {
            emaVal = candles.get(i).closePrice() * k + emaVal * (1 - k);
        }
        return emaVal;
    }

    private double volumeMa(List<CandleData> candles, int period, int offset) {
        int end = candles.size() - offset;
        int start = end - period;
        if (start < 0 || period <= 0) return Double.NaN;
        double sum = 0;
        for (int i = start; i < end; i++) sum += candles.get(i).volume();
        return sum / period;
    }

    // =========================================================================
    // RSI (Wilder smooth approximation)
    // =========================================================================

    private double rsi(List<CandleData> candles, int period, int offset) {
        int end = candles.size() - offset;
        if (end < period + 1) return Double.NaN;
        double avgGain = 0, avgLoss = 0;
        int start = end - period;
        for (int i = start; i < end; i++) {
            double change = candles.get(i).closePrice() - candles.get(i - 1).closePrice();
            if (change > 0) avgGain += change;
            else avgLoss += Math.abs(change);
        }
        avgGain /= period;
        avgLoss /= period;
        if (avgLoss < EPSILON) return 100.0;
        return 100.0 - (100.0 / (1.0 + avgGain / avgLoss));
    }

    // =========================================================================
    // MACD
    // =========================================================================

    private double macdLine(List<CandleData> candles, int offset) {
        double fast = ema(candles, 12, offset);
        double slow = ema(candles, 26, offset);
        if (Double.isNaN(fast) || Double.isNaN(slow)) return Double.NaN;
        return fast - slow;
    }

    private double macdSignalLine(List<CandleData> candles, int offset) {
        int end = candles.size() - offset;
        int sigPeriod = 9;
        int minRequired = 26 + sigPeriod;
        if (end < minRequired) return Double.NaN;
        // Build MACD line array and EMA(9) over it
        double k = 2.0 / (sigPeriod + 1);
        // Seed: first MACD value at index 25 (first valid slow EMA)
        double sig = 0;
        int seedIdx = 25;
        sig = ema(candles.subList(0, seedIdx + 1), 12, 0)
                - ema(candles.subList(0, seedIdx + 1), 26, 0);
        for (int i = seedIdx + 1; i < end; i++) {
            double m = ema(candles.subList(0, i + 1), 12, 0)
                     - ema(candles.subList(0, i + 1), 26, 0);
            if (!Double.isNaN(m)) sig = m * k + sig * (1 - k);
        }
        return sig;
    }

    // =========================================================================
    // Bollinger Bands
    // =========================================================================

    private double bollingerUpper(List<CandleData> candles, int period, int offset) {
        double mid = sma(candles, period, offset);
        double std = stdDev(candles, period, offset, mid);
        return Double.isNaN(mid) ? Double.NaN : mid + 2 * std;
    }

    private double bollingerLower(List<CandleData> candles, int period, int offset) {
        double mid = sma(candles, period, offset);
        double std = stdDev(candles, period, offset, mid);
        return Double.isNaN(mid) ? Double.NaN : mid - 2 * std;
    }

    private double stdDev(List<CandleData> candles, int period, int offset, double mean) {
        int end = candles.size() - offset;
        int start = end - period;
        if (start < 0 || Double.isNaN(mean)) return Double.NaN;
        double variance = 0;
        for (int i = start; i < end; i++) {
            double d = candles.get(i).closePrice() - mean;
            variance += d * d;
        }
        return Math.sqrt(variance / period);
    }

    // =========================================================================
    // ATR
    // =========================================================================

    private double atr(List<CandleData> candles, int period, int offset) {
        int end = candles.size() - offset;
        if (end < period + 1) return Double.NaN;
        double total = 0;
        for (int i = end - period; i < end; i++) {
            CandleData c = candles.get(i);
            CandleData prev = candles.get(i - 1);
            double tr = Math.max(c.highPrice() - c.lowPrice(),
                    Math.max(Math.abs(c.highPrice() - prev.closePrice()),
                             Math.abs(c.lowPrice()  - prev.closePrice())));
            total += tr;
        }
        return total / period;
    }

    // =========================================================================
    // Signal builder (AI / RiskEngine are downstream — no order submission here)
    // =========================================================================

    private StrategySignal toSignal(NoCodeAction action, double confidence,
                                    String symbol, String timeframe, String stratId,
                                    String reason, double price,
                                    NoCodeRiskSettings risk) {
        return switch (action) {
            case BUY -> StrategySignal.buy(symbol, timeframe, stratId, confidence, price,
                    risk != null && risk.hasStopLoss()  ? risk.calculateStopLossForBuy(price)  : 0,
                    risk != null && risk.hasTakeProfit() ? risk.calculateTakeProfitForBuy(price) : 0,
                    reason);
            case SELL -> StrategySignal.sell(symbol, timeframe, stratId, confidence, price,
                    risk != null && risk.hasStopLoss()  ? risk.calculateStopLossForSell(price)  : 0,
                    risk != null && risk.hasTakeProfit() ? risk.calculateTakeProfitForSell(price) : 0,
                    reason);
            case CLOSE_LONG -> StrategySignal.sell(symbol, timeframe, stratId, confidence,
                    price, 0, 0, "Close long: " + reason);
            case CLOSE_SHORT -> StrategySignal.buy(symbol, timeframe, stratId, confidence,
                    price, 0, 0, "Close short: " + reason);
            default -> StrategySignal.hold(symbol, timeframe, stratId, reason);
        };
    }
}
