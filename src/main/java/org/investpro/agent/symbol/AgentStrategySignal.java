package org.investpro.agent.symbol;

import java.util.Map;

public record AgentStrategySignal(
        SignalType signalType,
        double confidence,
        String reason,
        Map<String, String> metadata) {

    public AgentStrategySignal {
        signalType = signalType == null ? SignalType.NEUTRAL : signalType;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
