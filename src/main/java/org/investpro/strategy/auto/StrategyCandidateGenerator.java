package org.investpro.strategy.auto;

import java.util.List;

public interface StrategyCandidateGenerator {
    List<StrategyCandidate> generateCandidates(StrategyGenerationContext context);
}
