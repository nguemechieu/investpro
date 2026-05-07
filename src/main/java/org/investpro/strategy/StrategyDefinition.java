package org.investpro.strategy;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Builder
@Slf4j
public class StrategyDefinition {

    private final String name;
    private final String baseName;

    @Builder.Default
    private final StrategyParameters parameters = StrategyParameters.builder().build();
}