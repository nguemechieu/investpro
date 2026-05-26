package org.investpro.strategy.signals;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PipelineStrategyCatalog {
    private final CopyOnWriteArrayList<TradingStrategy> strategies = new CopyOnWriteArrayList<>();

    public PipelineStrategyCatalog() {
        register(new DefaultStarterStrategy());
    }

    public void register(TradingStrategy strategy) {
        if (strategy != null) {
            strategies.addIfAbsent(strategy);
        }
    }

    public List<TradingStrategy> strategies() {
        return List.copyOf(strategies);
    }
}
