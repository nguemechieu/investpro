package org.investpro.strategy.signals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class SignalAggregator {
    private static final BigDecimal MIN_CONFIDENCE = new BigDecimal("0.60");
    private static final BigDecimal MIN_EDGE = new BigDecimal("0.15");

    public SignalDecision aggregate(List<StrategySignal> signals) {
        if (signals == null || signals.isEmpty()) {
            return hold(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, "No strategy signals were available.");
        }

        BigDecimal buy = score(signals, TradingAction.BUY);
        BigDecimal sell = score(signals, TradingAction.SELL);
        BigDecimal hold = score(signals, TradingAction.HOLD)
                .add(score(signals, TradingAction.CLOSE).multiply(new BigDecimal("0.25")))
                .add(score(signals, TradingAction.REDUCE).multiply(new BigDecimal("0.25")));
        BigDecimal conflict = buy.min(sell);
        BigDecimal edge = buy.subtract(sell).abs();

        TradingAction action = buy.compareTo(sell) > 0 ? TradingAction.BUY : TradingAction.SELL;
        BigDecimal winning = buy.max(sell);
        List<String> reasons = new ArrayList<>();
        signals.stream().map(StrategySignal::reason).filter(reason -> !reason.isBlank()).forEach(reasons::add);

        if (winning.compareTo(MIN_CONFIDENCE) < 0) {
            reasons.add("Winning confidence is below the minimum threshold.");
            return new SignalDecision(TradingAction.HOLD, winning, buy, sell, hold, conflict, signals.size(),
                    List.of(), reasons, Instant.now(), Map.of("rule", "weak-confidence"));
        }
        if (edge.compareTo(MIN_EDGE) < 0) {
            reasons.add("Buy and sell scores are too close.");
            return new SignalDecision(TradingAction.HOLD, winning, buy, sell, hold, conflict, signals.size(),
                    List.of(), reasons, Instant.now(), Map.of("rule", "conflict"));
        }

        List<String> winners = signals.stream()
                .filter(signal -> signal.action() == action)
                .sorted(Comparator.comparing(StrategySignal::confidence).reversed())
                .map(StrategySignal::strategyName)
                .filter(name -> !name.isBlank())
                .distinct()
                .toList();

        return new SignalDecision(action, winning, buy, sell, hold, conflict, signals.size(),
                winners, reasons, Instant.now(), Map.of("rule", "weighted-confidence"));
    }

    private SignalDecision hold(BigDecimal buy, BigDecimal sell, BigDecimal hold, String reason) {
        return new SignalDecision(TradingAction.HOLD, BigDecimal.ZERO, buy, sell, hold, BigDecimal.ZERO, 0,
                List.of(), List.of(reason), Instant.now(), Map.of());
    }

    private BigDecimal score(List<StrategySignal> signals, TradingAction action) {
        return signals.stream()
                .filter(signal -> signal != null && signal.action() == action)
                .map(StrategySignal::confidence)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
