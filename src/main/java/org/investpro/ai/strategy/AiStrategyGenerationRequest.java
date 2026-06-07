package org.investpro.ai.strategy;

import org.investpro.ai.AiModelDefinition;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.models.trading.TradePair;

import java.util.Optional;

public record AiStrategyGenerationRequest(
        AiModelDefinition model,
        String prompt,
        Optional<TradePair> optionalPair,
        Optional<Timeframe> optionalTimeframe,
        boolean overwriteCurrentStrategy,
        boolean disclaimerAccepted) {
}
