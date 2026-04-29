package org.investpro.investpro.ai;


import org.investpro.investpro.CandleDataSupplier;
import org.investpro.investpro.Exchange;
import org.investpro.investpro.indicators.IndicatorCalculator;
import org.investpro.investpro.models.CandleData;
import org.investpro.investpro.models.TradePair;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class StrategyRecommendationEngine {
    private static final Logger logger = LoggerFactory.getLogger(StrategyRecommendationEngine.class);
    private static final Map<String, Integer> TIMEFRAME_CANDIDATES = Map.of(
            "5m", 300,
            "15m", 900,
            "1h", 3600,
            "4h", 14400
    );
    private static final List<String> STRATEGIES = List.of(
            "AI Hybrid",
            "Momentum Breakout",
            "Trend Follow",
            "Mean Reversion",
            "Scalper",
            "Market Making"
    );

    private final Exchange exchange;

    public StrategyRecommendationEngine(Exchange exchange) {
        this.exchange = exchange;
    }

    public StrategyRecommendation recommend(List<String> symbols, String preferredSymbol) {
        if (symbols == null || symbols.isEmpty()) {
            return null;
        }

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (preferredSymbol != null && !preferredSymbol.isBlank()) {
            candidates.add(preferredSymbol);
        }
        candidates.addAll(symbols.stream().limit(6).toList());

        RecommendationCandidate best = null;
        for (String symbol : candidates) {
            RecommendationCandidate candidate = recommendForSymbol(symbol);
            if (candidate == null) {
                continue;
            }
            if (best == null || candidate.score() > best.score()) {
                best = candidate;
            }
        }

        if (best == null) {
            return null;
        }

        String status = best.predictorSignal().available()
                ? "Auto-routed with predictor support"
                : "Auto-routed from local backtest";
        String note = String.format(
                "Backtest %.2f score | %d trades | %.0f%% win rate%s",
                best.score(),
                best.tradeCount(),
                best.winRate() * 100.0,
                best.predictorSignal().available()
                        ? " | Python predictor " + best.predictorSignal().label() + " " + Math.round(best.predictorSignal().confidence() * 100) + "%"
                        : ""
        );
        return new StrategyRecommendation(best.symbol(), best.strategy(), best.timeframe(), status, note);
    }

    private RecommendationCandidate recommendForSymbol(String symbol) {
        String[] parts = symbol.split("/");
        if (parts.length != 2) {
            return null;
        }

        TradePair tradePair;
        try {
            tradePair = new TradePair(parts[0], parts[1]);
        } catch (Exception e) {
            logger.debug("Unable to build trade pair for {}: {}", symbol, e.getMessage());
            return null;
        }
        RecommendationCandidate best = null;
        Set<Integer> supportedGranularity = exchange.granularity();
        for (Map.Entry<String, Integer> timeframe : TIMEFRAME_CANDIDATES.entrySet()) {
            if (!supportedGranularity.contains(timeframe.getValue())) {
                continue;
            }

            List<CandleData> candles = fetchCandles(tradePair, timeframe.getValue());
            if (candles.size() < 60) {
                continue;
            }
            PredictorSignal predictorSignal = resolvePredictorSignal(candles);

            for (String strategy : STRATEGIES) {
                BacktestMetrics metrics = backtestStrategy(strategy, candles, predictorSignal);
                if (metrics.tradeCount() < 6) {
                    continue;
                }
                RecommendationCandidate candidate = new RecommendationCandidate(
                        symbol,
                        strategy,
                        timeframe.getKey(),
                        metrics.score(),
                        metrics.tradeCount(),
                        metrics.winRate(),
                        predictorSignal
                );
                if (best == null || candidate.score() > best.score()) {
                    best = candidate;
                }
            }
        }

        return best;
    }

    private List<CandleData> fetchCandles(TradePair tradePair, int secondsPerCandle) {
        try {
            CandleDataSupplier supplier = exchange.getCandleDataSupplier(secondsPerCandle, tradePair);
            List<CandleData> candles = supplier.get().get(15, TimeUnit.SECONDS);
            return candles == null ? List.of() : candles;
        } catch (Exception e) {
            logger.debug("Unable to fetch candles for {} @ {} seconds: {}", tradePair, secondsPerCandle, e.getMessage());
            return List.of();
        }
    }

    private BacktestMetrics backtestStrategy(String strategy, List<CandleData> candles, PredictorSignal predictorSignal) {
        double cumulativeReturn = 0.0;
        int trades = 0;
        int wins = 0;
        for (int i = 40; i < candles.size() - 1; i++) {
            List<CandleData> window = candles.subList(Math.max(0, i - 120), i + 1);
            Bias signal = resolveSignal(strategy, window, predictorSignal);
            if (signal == Bias.NEUTRAL) {
                continue;
            }

            CandleData current = candles.get(i);
            CandleData next = candles.get(i + 1);
            double change = percentageMove(current.getClosePrice(), next.getClosePrice());
            double tradeReturn = signal == Bias.BULLISH ? change : -change;
            cumulativeReturn += tradeReturn;
            trades++;
            if (tradeReturn > 0) {
                wins++;
            }
        }

        double winRate = trades == 0 ? 0.0 : (double) wins / trades;
        double score = cumulativeReturn + (winRate * 20.0) + Math.min(trades, 20) * 0.15;
        return new BacktestMetrics(score, trades, winRate);
    }

    private Bias resolveSignal(String strategy, List<CandleData> candles, PredictorSignal predictorSignal) {
        if (candles.isEmpty()) {
            return Bias.NEUTRAL;
        }

        CandleData latest = candles.getLast();
        double close = latest.getClosePrice();
        double emaFast = IndicatorCalculator.calculateEMA(candles, 10);
        double emaSlow = IndicatorCalculator.calculateEMA(candles, 30);
        double macd = IndicatorCalculator.calculateMACD(candles);
        double rsi = IndicatorCalculator.calculateRSI(candles, 14);
        double bbUpper = IndicatorCalculator.calculateBollingerUpper(candles, 20);
        double bbLower = IndicatorCalculator.calculateBollingerLower(candles, 20);
        double stochastic = IndicatorCalculator.calculateStochastic(candles, 14);
        double atr = calculateAtr(candles, 14);
        double rollingHigh = highestHigh(candles, 20);
        double rollingLow = lowestLow(candles, 20);

        return switch (strategy) {
            case "AI Hybrid" -> {
                if (predictorSignal.available() && predictorSignal.confidence() >= 0.58) {
                    yield "BUY".equalsIgnoreCase(predictorSignal.label()) || "up".equalsIgnoreCase(predictorSignal.label())
                            ? Bias.BULLISH
                            : Bias.BEARISH;
                }
                if (emaFast > emaSlow && macd >= 0 && rsi < 72) {
                    yield Bias.BULLISH;
                }
                if (emaFast < emaSlow && macd <= 0 && rsi > 28) {
                    yield Bias.BEARISH;
                }
                yield Bias.NEUTRAL;
            }
            case "Momentum Breakout" -> {
                if (close >= rollingHigh * 0.998 && macd > 0 && rsi > 55 && rsi < 78) {
                    yield Bias.BULLISH;
                }
                if (close <= rollingLow * 1.002 && macd < 0 && rsi < 45 && rsi > 22) {
                    yield Bias.BEARISH;
                }
                yield Bias.NEUTRAL;
            }
            case "Trend Follow" -> {
                if (emaFast > emaSlow && close > emaFast && macd >= 0) {
                    yield Bias.BULLISH;
                }
                if (emaFast < emaSlow && close < emaFast && macd <= 0) {
                    yield Bias.BEARISH;
                }
                yield Bias.NEUTRAL;
            }
            case "Mean Reversion" -> {
                if (close <= bbLower && rsi < 35) {
                    yield Bias.BULLISH;
                }
                if (close >= bbUpper && rsi > 65) {
                    yield Bias.BEARISH;
                }
                yield Bias.NEUTRAL;
            }
            case "Scalper" -> {
                if (emaFast > emaSlow && stochastic < 75 && macd > -0.05) {
                    yield Bias.BULLISH;
                }
                if (emaFast < emaSlow && stochastic > 25 && macd < 0.05) {
                    yield Bias.BEARISH;
                }
                yield Bias.NEUTRAL;
            }
            case "Market Making" -> {
                double mid = (bbUpper + bbLower) / 2.0;
                if (mid <= 0 || close <= 0) {
                    yield Bias.NEUTRAL;
                }
                double atrRatio = atr / close;
                if (atrRatio < 0.006 && close < mid * 0.9985) {
                    yield Bias.BULLISH;
                }
                if (atrRatio < 0.006 && close > mid * 1.0015) {
                    yield Bias.BEARISH;
                }
                yield Bias.NEUTRAL;
            }
            default -> Bias.NEUTRAL;
        };
    }

    private PredictorSignal resolvePredictorSignal(List<CandleData> candles) {
        if (!PredictorRuntimeManager.ensureAvailable(Duration.ofSeconds(8))) {
            return PredictorSignal.unavailable();
        }

        InvestProAIPredictor predictor = new InvestProAIPredictor(
                System.getProperty("investpro.ai.host", "localhost"),
                Integer.getInteger("investpro.ai.port", 50051)
        );
        try {
            InvestProAIPredictor.MarketDataRequest request = buildPredictorRequest(candles);
            List<InvestProAIPredictor.PredictionResponse> responses = predictor.streamBatchPredict(List.of(request)).get(5, TimeUnit.SECONDS);
            if (responses.isEmpty()) {
                return PredictorSignal.unavailable();
            }
            InvestProAIPredictor.PredictionResponse response = responses.getFirst();
            return PredictorSignal.available(response.getPrediction(), response.getConfidence());
        } catch (Exception ex) {
            logger.debug("Predictor recommendation unavailable: {}", ex.getMessage());
            return PredictorSignal.unavailable();
        } finally {
            predictor.shutdown();
        }
    }

    private InvestProAIPredictor.MarketDataRequest buildPredictorRequest(List<CandleData> candles) {
        List<CandleData> featureCandles = candles.subList(Math.max(0, candles.size() - 20), candles.size());
        CandleData latest = candles.getLast();
        return InvestProAIPredictor.MarketDataRequest.newBuilder()
                .setOpen(latest.getOpenPrice())
                .setHigh(latest.getHighPrice())
                .setLow(latest.getLowPrice())
                .setClose(latest.getClosePrice())
                .setVolume(latest.getVolume())
                .setAtr(calculateAtr(candles, 14))
                .setRsi(IndicatorCalculator.calculateRSI(candles, 14))
                .setBbUpper(IndicatorCalculator.calculateBollingerUpper(featureCandles, 20))
                .setBbLower(IndicatorCalculator.calculateBollingerLower(featureCandles, 20))
                .setStoch(IndicatorCalculator.calculateStochastic(candles, 14))
                .setMacd(IndicatorCalculator.calculateMACD(candles))
                .build();
    }

    private static double percentageMove(double entry, double exit) {
        if (entry == 0) {
            return 0;
        }
        return ((exit - entry) / entry) * 100.0;
    }

    private static double highestHigh(List<CandleData> candles, int lookback) {
        double highest = Double.NEGATIVE_INFINITY;
        for (int i = Math.max(0, candles.size() - lookback); i < candles.size(); i++) {
            highest = Math.max(highest, candles.get(i).getHighPrice());
        }
        return highest;
    }

    private static double lowestLow(List<CandleData> candles, int lookback) {
        double lowest = Double.POSITIVE_INFINITY;
        for (int i = Math.max(0, candles.size() - lookback); i < candles.size(); i++) {
            lowest = Math.min(lowest, candles.get(i).getLowPrice());
        }
        return lowest;
    }

    private static double calculateAtr(@NotNull List<CandleData> candles, int period) {
        if (candles.size() < period + 1) {
            return 0;
        }

        double sum = 0;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            double high = candles.get(i).getHighPrice();
            double low = candles.get(i).getLowPrice();
            double prevClose = candles.get(i - 1).getClosePrice();
            sum += Math.max(high - low, Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
        }
        return sum / period;
    }

    public record StrategyRecommendation(
            String symbol,
            String strategy,
            String timeframe,
            String status,
            String note
    ) {
    }

    private record RecommendationCandidate(
            String symbol,
            String strategy,
            String timeframe,
            double score,
            int tradeCount,
            double winRate,
            PredictorSignal predictorSignal
    ) {
    }

    private record BacktestMetrics(
            double score,
            int tradeCount,
            double winRate
    ) {
    }

    private record PredictorSignal(
            boolean available,
            String label,
            double confidence
    ) {
        private static PredictorSignal unavailable() {
            return new PredictorSignal(false, "unknown", 0.0);
        }

        private static PredictorSignal available(String label, double confidence) {
            return new PredictorSignal(true, label, confidence);
        }
    }

    private enum Bias {
        BULLISH,
        BEARISH,
        NEUTRAL
    }
}
