package org.investpro.strategy.nocode;

import lombok.extern.slf4j.Slf4j;
import org.investpro.model.CandleData;
import org.investpro.strategy.StrategyContext;
import org.investpro.strategy.StrategySignal;
import org.investpro.utils.Side;

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
 * <p>This class is stateless with respect to strategy definition state and is
 * thread-safe when using separate instances per evaluation context.</p>
 */
@Slf4j
public class NoCodeStrategyRuntime {

    private static final double EPSILON = 1e-9;

    private final CompiledNoCodeStrategy compiled;

    /**
     * Creates a runtime for the given compiled strategy.
     *
     * @param compiled the compiled strategy (must be valid)
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

        if (candles == null || candles.size() < compiled.getWarmupBars()) {
            log.debug("Insufficient candles ({} < {}); emitting HOLD",
                    candles == null ? 0 : candles.size(), compiled.getWarmupBars());
            return StrategySignal.hold(def.getName(), "Insufficient data for warmup");
        }

        // Evaluate entry rules first
        for (NoCodeRule rule : def.getEntryRules()) {
            if (evaluateRule(rule, candles)) {
                return actionToSignal(rule.getAction(), rule.getConfidence(), def.getName(),
                        rule.toPreviewText(), ctx.getCurrentPrice(), def.getRiskSettings());
            }
        }

        // Evaluate exit rules
        for (NoCodeRule rule : def.getExitRules()) {
            if (evaluateRule(rule, candles)) {
                return actionToSignal(rule.getAction(), rule.getConfidence(), def.getName(),
                        rule.toPreviewText(), ctx.getCurrentPrice(), def.getRiskSettings());
            }
        }

        return StrategySignal.hold(def.getName(), "No rule fired");
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
                    yield leftValue >= lo && leftValue <= hi;
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
    // Indicator computations
    // =========================================================================

    /**
     * Computes the indicator value for the given reference.
     *
     * @param ref        indicator reference
     * @param candles    full candle list (newest = last element)
     * @param usePrevious if true, compute using the one-bar-ago candle as current
     * @return computed value, or Double.NaN if insufficient data
     */
    private double computeIndicator(NoCodeIndicatorReference ref,
                                    List<CandleData> candles,
                                    boolean usePrevious) {
        if (ref == null) return Double.NaN;
        int offset = usePrevious ? 1 : 0;
        return switch (ref.getType()) {
            case PRICE_CLOSE -> getClose(candles, offset);
            case PRICE_OPEN -> getOpen(candles, offset);
            case PRICE_HIGH -> getHigh(candles, offset);
            case PRICE_LOW -> getLow(candles, offset);
            case VOLUME -> getVolume(candles, offset);
            case SMA -> sma(candles, ref.effectivePeriod(), offset);
            case EMA -> ema(candles, ref.effectivePeriod(), offset);
            case RSI -> rsi(candles, ref.effectivePeriod(), offset);
            case MACD -> macdLine(candles, offset);
            case MACD_SIGNAL -> macdSignalLine(candles, offset);
            case BOLLINGER_UPPER -> bollingerUpper(candles, ref.effectivePeriod(), offset);
            case BOLLINGER_LOWER -> bollingerLower(candles, ref.effectivePeriod(), offset);
            case BOLLINGER_MID -> sma(candles, ref.effectivePeriod(), offset);
            case ATR -> atr(candles, ref.effectivePeriod(), offset);
            case VOLUME_MA -> volumeMa(candles, ref.effectivePeriod(), offset);
        };
    }

    // =========================================================================
    // Candle accessors
    // =========================================================================

    private double getClose(List<CandleData> candles, int offset) {
        int idx = candles.size() - 1 - offset;
        return idx >= 0 ? candles.get(idx).getClose() : Double.NaN;
    }

    private double getOpen(List<CandleData> candles, int offset) {
        int idx = candles.size() - 1 - offset;
        return idx >= 0 ? candles.get(idx).getOpen() : Double.NaN;
    }

    private double getHigh(List<CandleData> candles, int offset) {
        int idx = candles.size() - 1 - offset;
        return idx >= 0 ? candles.get(idx).getHigh() : Double.NaN;
    }

    private double getLow(List<CandleData> candles, int offset) {
        int idx = candles.size() - 1 - offset;
        return idx >= 0 ? candles.get(idx).getLow() : Double.NaN;
    }

    private double getVolume(List<CandleData> candles, int offset) {
        int idx = candles.size() - 1 - offset;
        return idx >= 0 ? candles.get(idx).getVolume() : Double.NaN;
    }

    // =========================================================================
    // Moving averages
    // =========================================================================

    private double sma(List<CandleData> candles, int period, int offset) {
        int end = candles.size() - offset;
        int start = end - period;
        if (start < 0) return Double.NaN;
        double sum = 0;
        for (int i = start; i < end; i++) sum += candles.get(i).getClose();
        return sum / period;
    }

    private double ema(List<CandleData> candles, int period, int offset) {
        int end = candles.size() - offset;
        if (end < period) return Double.NaN;
        double k = 2.0 / (period + 1);
        // Seed with SMA of first `period` bars
        double emaVal = 0;
        int seedStart = end - candles.size(); // always 0 effectively
        for (int i = 0; i < period; i++) emaVal += candles.get(i).getClose();
        emaVal /= period;
        for (int i = period; i < end; i++) {
            emaVal = candles.get(i).getClose() * k + emaVal * (1 - k);
        }
        return emaVal;
    }

    private double volumeMa(List<CandleData> candles, int period, int offset) {
        int end = candles.size() - offset;
        int start = end - period;
        if (start < 0) return Double.NaN;
        double sum = 0;
        for (int i = start; i < end; i++) sum += candles.get(i).getVolume();
        return sum / period;
    }

    // =========================================================================
    // RSI
    // =========================================================================

    private double rsi(List<CandleData> candles, int period, int offset) {
        int end = candles.size() - offset;
        if (end < period + 1) return Double.NaN;
        double avgGain = 0, avgLoss = 0;
        // First period: simple average
        for (int i = end - period; i < end; i++) {
            double change = candles.get(i).getClose() - candles.get(i - 1).getClose();
            if (change > 0) avgGain += change;
            else avgLoss += Math.abs(change);
        }
        avgGain /= period;
        avgLoss /= period;
        if (avgLoss == 0) return 100;
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
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
        // Build a synthetic list of MACD values and EMA(9) over them
        int size = candles.size() - offset;
        if (size < 35) return Double.NaN;
        double[] macdValues = new double[size];
        for (int i = 0; i < size; i++) {
            List<CandleData> sub = candles.subList(0, i + 1);
            macdValues[i] = macdLine(sub, 0);
        }
        // EMA(9) over MACD values
        int sigPeriod = 9;
        if (size < 26 + sigPeriod) return Double.NaN;
        double k = 2.0 / (sigPeriod + 1);
        double sig = macdValues[26]; // seed
        for (int i = 27; i < size; i++) {
            if (!Double.isNaN(macdValues[i])) {
                sig = macdValues[i] * k + sig * (1 - k);
            }
        }
        return sig;
    }

    // =========================================================================
    // Bollinger Bands
    // =========================================================================

    private double bollingerUpper(List<CandleData> candles, int period, int offset) {
        return bollingerMid(candles, period, offset) + 2 * bollingerStdDev(candles, period, offset);
    }

    private double bollingerLower(List<CandleData> candles, int period, int offset) {
        return bollingerMid(candles, period, offset) - 2 * bollingerStdDev(candles, period, offset);
    }

    private double bollingerMid(List<CandleData> candles, int period, int offset) {
        return sma(candles, period, offset);
    }

    private double bollingerStdDev(List<CandleData> candles, int period, int offset) {
        int end = candles.size() - offset;
        int start = end - period;
        if (start < 0) return Double.NaN;
        double mean = sma(candles, period, offset);
        double variance = 0;
        for (int i = start; i < end; i++) {
            double diff = candles.get(i).getClose() - mean;
            variance += diff * diff;
        }
        return Math.sqrt(variance / period);
    }

    // =========================================================================
    // ATR
    // =========================================================================

    private double atr(List<CandleData> candles, int period, int offset) {
        int end = candles.size() - offset;
        if (end < period + 1) return Double.NaN;
        double atrVal = 0;
        for (int i = end - period; i < end; i++) {
            CandleData c = candles.get(i);
            CandleData prev = candles.get(i - 1);
            double tr = Math.max(c.getHigh() - c.getLow(),
                    Math.max(Math.abs(c.getHigh() - prev.getClose()),
                            Math.abs(c.getLow() - prev.getClose())));
            atrVal += tr;
        }
        return atrVal / period;
    }

    // =========================================================================
    // Signal builder
    // =========================================================================

    private StrategySignal actionToSignal(NoCodeAction action, double confidence, String strategyName,
                                          String reason, double currentPrice,
                                          NoCodeRiskSettings risk) {
        return switch (action) {
            case BUY -> StrategySignal.buy(strategyName, confidence, reason,
                    risk != null ? risk.calculateStopLossForBuy(currentPrice) : 0,
                    risk != null ? risk.calculateTakeProfitForBuy(currentPrice) : 0);
            case SELL -> StrategySignal.sell(strategyName, confidence, reason,
                    risk != null ? risk.calculateStopLossForSell(currentPrice) : 0,
                    risk != null ? risk.calculateTakeProfitForSell(currentPrice) : 0);
            case CLOSE_LONG -> StrategySignal.sell(strategyName, confidence,
                    "Close long: " + reason, 0, 0);
            case CLOSE_SHORT -> StrategySignal.buy(strategyName, confidence,
                    "Close short: " + reason, 0, 0);
            default -> StrategySignal.hold(strategyName, reason);
        };
    }
}
