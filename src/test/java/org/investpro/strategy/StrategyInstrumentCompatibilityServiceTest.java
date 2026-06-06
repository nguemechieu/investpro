package org.investpro.strategy;

import org.investpro.models.market.AssetClass;
import org.investpro.models.market.ContractType;
import org.investpro.models.market.MarketInstrument;
import org.investpro.models.market.MarketType;
import org.investpro.models.market.TradingEnvironment;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StrategyInstrumentCompatibilityServiceTest {

    private final StrategyInstrumentCompatibilityService service = new StrategyInstrumentCompatibilityService();

    @Test
    void cryptoMomentumSupportsSpotAndPerpetual() {
        StrategyDefinition strategy = StrategyDefinition.builder()
                .name("Crypto Momentum")
                .marketCompatibility(Set.of(
                        StrategyMarketCompatibility.SPOT,
                        StrategyMarketCompatibility.PERPETUAL))
                .build();

        assertTrue(service.supports(strategy, instrument(MarketType.SPOT)));
        assertTrue(service.supports(strategy, instrument(
                AssetClass.CRYPTO,
                MarketType.DERIVATIVE,
                ContractType.PERPETUAL)));
    }

    @Test
    void gapStrategySupportsEquityAndIndex() {
        StrategyDefinition strategy = StrategyDefinition.builder()
                .name("Gap Strategy")
                .marketCompatibility(Set.of(
                        StrategyMarketCompatibility.EQUITY,
                        StrategyMarketCompatibility.INDEX))
                .build();

        assertTrue(service.supports(strategy, instrument(
                AssetClass.EQUITY,
                MarketType.SPOT,
                ContractType.CASH)));
        assertTrue(service.supports(strategy, instrument(
                AssetClass.INDEX,
                MarketType.DERIVATIVE,
                ContractType.FUTURE)));
        assertFalse(service.supports(strategy, instrument(MarketType.FX)));
    }

    @Test
    void carryTrendSupportsFxAndCommodityTrendSupportsCommodity() {
        StrategyDefinition carry = StrategyDefinition.builder()
                .name("Carry Trend")
                .marketCompatibility(Set.of(StrategyMarketCompatibility.FX))
                .build();
        StrategyDefinition commodity = StrategyDefinition.builder()
                .name("Commodity Trend")
                .marketCompatibility(Set.of(StrategyMarketCompatibility.COMMODITY))
                .build();

        assertTrue(service.supports(carry, instrument(MarketType.FX)));
        assertTrue(service.supports(commodity, instrument(
                AssetClass.COMMODITY,
                MarketType.DERIVATIVE,
                ContractType.FUTURE)));
    }

    @Test
    void unknownIsBlockedForBotLiveTrading() {
        StrategyDefinition strategy = StrategyDefinition.builder()
                .name("Universal")
                .marketCompatibility(Set.of(StrategyMarketCompatibility.ALL))
                .build();

        assertFalse(service.supports(strategy, instrument(MarketType.UNKNOWN)));
    }

    private MarketInstrument instrument(MarketType marketType) {
        return instrument(
                marketType == MarketType.SPOT ? AssetClass.CRYPTO : AssetClass.UNKNOWN,
                marketType,
                marketType == MarketType.SPOT ? ContractType.CASH : ContractType.UNKNOWN);
    }

    private MarketInstrument instrument(AssetClass assetClass, MarketType marketType, ContractType contractType) {
        return new MarketInstrument(
                "TEST",
                marketType.name(),
                marketType.name(),
                null,
                assetClass,
                marketType,
                contractType,
                "",
                null,
                null,
                TradingEnvironment.UNKNOWN,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                null,
                marketType != MarketType.SPOT,
                false,
                marketType == MarketType.SPOT,
                marketType != MarketType.SPOT,
                null,
                null);
    }
}
