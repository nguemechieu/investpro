package org.investpro.strategy;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.enums.StrategyCategory;
import org.investpro.enums.AssetClass;
import org.investpro.enums.ContractType;
import org.investpro.enums.timeframe.Timeframe;

import java.util.HashSet;
import java.util.Set;

/**
 * Metadata describing a trading strategy.
 * Defines capabilities, compatibility, and classification.
 */
@Getter
@Builder
@Slf4j
public class StrategyMetadata {
    private final String strategyId;
    private final String displayName;
    private final String description;
    private final StrategyCategory category;

    @Builder.Default
    private final Set<AssetClass> supportedAssetClasses = new HashSet<>();

    @Builder.Default
    private final Set<ContractType> supportedContractTypes = new HashSet<>();

    @Builder.Default
    private final Set<Timeframe> supportedTimeframes = new HashSet<>();

    @Builder.Default
    private final int minimumBarsRequired = 50;

    private final String expectedHoldingPeriod; // e.g., "minutes", "hours", "days"

    @Builder.Default
    private final RiskLevel riskLevel = RiskLevel.MEDIUM;

    @Builder.Default
    private final String version = "1.0.0";

    private final String author;

    @Builder.Default
    private final boolean enabled = true;

    @Getter
    public enum RiskLevel {
        LOW("Low Risk", "Conservative strategy, low volatility expected"),
        MEDIUM("Medium Risk", "Balanced strategy"),
        HIGH("High Risk", "Aggressive strategy, high volatility expected"),
        EXTREME("Extreme Risk", "Highly volatile or leveraged");

        private final String displayName;
        private final String description;

        RiskLevel(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

    }

    public boolean supportsAssetClass(AssetClass assetClass) {
        return supportedAssetClasses.isEmpty() || supportedAssetClasses.contains(assetClass);
    }

    public boolean supportsContractType(ContractType contractType) {
        return supportedContractTypes.isEmpty() || supportedContractTypes.contains(contractType);
    }

    public boolean supportsTimeframe(Timeframe timeframe) {
        return supportedTimeframes.isEmpty() || supportedTimeframes.contains(timeframe);
    }
}
