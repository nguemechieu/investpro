package org.investpro.terminal.autotrading;

import org.investpro.terminal.domain.Asset;
import org.investpro.terminal.domain.AssetClass;
import org.investpro.terminal.domain.Instrument;
import org.investpro.terminal.domain.InstrumentId;
import org.investpro.terminal.domain.MarketTick;
import org.investpro.terminal.domain.StrategySignal;
import org.investpro.terminal.domain.TradingStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradabilityServiceTest {

    private final TradabilityService service = new TradabilityService();

    @Test
    void connectedValidSymbolPassesAllChecks() {
        TradabilityResult result = service.evaluate(context(instrument("BTC/USD", AssetClass.CRYPTO, Map.of()), market(), policy()));

        assertTrue(result.tradable());
        assertEquals(TradabilityFailureReason.NONE, result.failureReason());
    }

    @Test
    void disconnectedExchangeIsRejected() {
        TradabilityResult result = service.evaluate(contextBuilder()
                .connectionState(ExchangeConnectionState.DISCONNECTED)
                .build());

        assertFalse(result.tradable());
        assertEquals(TradabilityFailureReason.EXCHANGE_DISCONNECTED, result.failureReason());
    }

    @Test
    void wideSpreadLowLiquidityLowVolumeAndMissingMarketDataAreRejected() {
        assertEquals(
                TradabilityFailureReason.MARKET_DATA_MISSING,
                service.evaluate(context(instrument("BTC/USD", AssetClass.CRYPTO, Map.of()), MarketQualitySnapshot.missing(), policy()))
                        .failureReason());

        assertEquals(
                TradabilityFailureReason.SPREAD_TOO_WIDE,
                service.evaluate(context(instrument("BTC/USD", AssetClass.CRYPTO, Map.of()),
                        new MarketQualitySnapshot(tick("BTC/USD"), 1.0, 90, 200000, Instant.now()),
                        policy()))
                        .failureReason());

        assertEquals(
                TradabilityFailureReason.LIQUIDITY_TOO_LOW,
                service.evaluate(context(instrument("BTC/USD", AssetClass.CRYPTO, Map.of()),
                        new MarketQualitySnapshot(tick("BTC/USD"), 0.1, 10, 200000, Instant.now()),
                        policy()))
                        .failureReason());

        assertEquals(
                TradabilityFailureReason.VOLUME_TOO_LOW,
                service.evaluate(context(instrument("BTC/USD", AssetClass.CRYPTO, Map.of()),
                        new MarketQualitySnapshot(tick("BTC/USD"), 0.1, 90, 100, Instant.now()),
                        policy()))
                        .failureReason());
    }

    @Test
    void strategyRiskAndDuplicateOrderGatesBlockOrders() {
        assertEquals(
                TradabilityFailureReason.STRATEGY_NOT_ASSIGNED,
                service.evaluate(contextBuilder().strategyAssigned(false).build()).failureReason());

        assertEquals(
                TradabilityFailureReason.STRATEGY_SIGNAL_HOLD,
                service.evaluate(contextBuilder()
                        .latestSignal(signal("HOLD"))
                        .build())
                        .failureReason());

        assertEquals(
                TradabilityFailureReason.RISK_REJECTED,
                service.evaluate(contextBuilder().riskAllowed(false).build()).failureReason());

        assertEquals(
                TradabilityFailureReason.DUPLICATE_ORDER_EXISTS,
                service.evaluate(contextBuilder().duplicateOrderExists(true).build()).failureReason());
    }

    @Test
    void blocklistedAndHaltedSymbolsAreRejected() {
        SymbolTradingPolicy blockedPolicy = new SymbolTradingPolicy(
                true, true, true, 0, 25,
                java.time.Duration.ofSeconds(60), java.time.Duration.ofSeconds(300),
                true, true, true, true, false,
                Set.of("BTC/USD"), Set.of(AssetClass.CRYPTO),
                100000, 0.30, 60, 1, 1,
                true, true, true, java.time.Duration.ofSeconds(15),
                false, true, false, 70, 0.50, true);

        assertEquals(
                TradabilityFailureReason.SYMBOL_BLOCKLISTED,
                service.evaluate(context(instrument("BTC/USD", AssetClass.CRYPTO, Map.of()), market(), blockedPolicy))
                        .failureReason());

        assertEquals(
                TradabilityFailureReason.SYMBOL_NOT_ACTIVE,
                service.evaluate(context(haltedInstrument(), market(), policy())).failureReason());
    }

    @Test
    void stellarUnknownIssuerAndMissingTrustlineAreRejected() {
        Instrument unknownIssuer = instrument("BTCLN/XLM", AssetClass.CRYPTO_STELLAR, Map.of());

        assertEquals(
                TradabilityFailureReason.STELLAR_ISSUER_UNKNOWN,
                service.evaluate(context(unknownIssuer, market(), policy())).failureReason());

        Instrument trustlineMissing = stellarInstrument(Map.of(
                "stellar.issuerAware", true,
                "stellar.baseTrusted", true,
                "stellar.quoteTrusted", true,
                "stellar.baseTrustlineRequired", true,
                "stellar.quoteTrustlineRequired", false,
                "stellar.tradeable", true));

        assertEquals(
                TradabilityFailureReason.STELLAR_TRUSTLINE_REQUIRED,
                service.evaluate(context(trustlineMissing, stellarMarket(), policy())).failureReason());
    }

    @Test
    void stellarReversedPairPassesWhenAllowed() {
        Instrument reversed = stellarInstrument(Map.of(
                "stellar.issuerAware", true,
                "stellar.baseTrusted", true,
                "stellar.quoteTrusted", true,
                "stellar.baseTrustlineRequired", true,
                "stellar.baseTrustlineExists", true,
                "stellar.quoteTrustlineRequired", false,
                "stellar.tradeable", true,
                "stellar.usingInvertedOrderBook", true));

        TradabilityResult result = service.evaluate(context(reversed, stellarMarket(), policy()));

        assertTrue(result.tradable());
    }

    private TradabilityContext context(Instrument instrument, MarketQualitySnapshot market, SymbolTradingPolicy policy) {
        return contextBuilder()
                .instrument(instrument)
                .marketQuality(market)
                .policy(policy)
                .build();
    }

    private ContextBuilder contextBuilder() {
        return new ContextBuilder();
    }

    private SymbolTradingPolicy policy() {
        return new SymbolTradingPolicy(
                true, true, true, 0, 25,
                java.time.Duration.ofSeconds(60), java.time.Duration.ofSeconds(300),
                true, true, true, true, false,
                Set.of(), Set.of(AssetClass.CRYPTO, AssetClass.CRYPTO_STELLAR, AssetClass.FOREX, AssetClass.EQUITY),
                100000, 0.30, 60, 1, 1,
                true, true, true, java.time.Duration.ofSeconds(15),
                false, true, false, 70, 0.50, true);
    }

    private MarketQualitySnapshot market() {
        return new MarketQualitySnapshot(tick("BTC/USD"), 0.1, 90, 200000, Instant.now());
    }

    private MarketQualitySnapshot stellarMarket() {
        return new MarketQualitySnapshot(tick("BTCLN/XLM"), 0.2, 80, 200000, Instant.now());
    }

    private MarketTick tick(String symbol) {
        return new MarketTick(
                new InstrumentId("TEST", symbol, symbol),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(100.1),
                BigDecimal.valueOf(100.05),
                BigDecimal.valueOf(200000),
                Instant.now());
    }

    private StrategySignal signal(String action) {
        return new StrategySignal(
                "trend",
                new InstrumentId("TEST", "BTC/USD", "BTC/USD"),
                action,
                0.8,
                "",
                "",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                "MARKET",
                Instant.now());
    }

    private Instrument stellarInstrument(Map<String, Object> metadata) {
        return instrument("BTCLN/XLM", AssetClass.CRYPTO_STELLAR, metadata);
    }

    private Instrument haltedInstrument() {
        Instrument base = instrument("BTC/USD", AssetClass.CRYPTO, Map.of());
        return new Instrument(
                base.id(), base.baseAsset(), base.quoteAsset(), base.displayName(), base.assetClass(), base.venue(),
                base.tickSize(), base.lotSize(), base.minOrderSize(), base.quoteIncrement(), base.baseIncrement(),
                TradingStatus.HALTED, false, false, true, true, base.metadata());
    }

    private Instrument instrument(String symbol, AssetClass assetClass, Map<String, Object> metadata) {
        String[] parts = symbol.split("/", 2);
        return new Instrument(
                new InstrumentId("TEST", symbol, symbol),
                new Asset(parts[0], parts[0], assetClass, "", ""),
                new Asset(parts[1], parts[1], assetClass, "", ""),
                symbol,
                assetClass,
                "Test",
                BigDecimal.valueOf(0.01),
                BigDecimal.valueOf(0.001),
                BigDecimal.valueOf(0.001),
                BigDecimal.valueOf(0.01),
                BigDecimal.valueOf(0.001),
                TradingStatus.ACTIVE,
                false,
                false,
                true,
                true,
                metadata);
    }

    private final class ContextBuilder {
        private Instrument instrument = TradabilityServiceTest.this.instrument("BTC/USD", AssetClass.CRYPTO, Map.of());
        private SymbolTradingPolicy policy = TradabilityServiceTest.this.policy();
        private ExchangeConnectionState connectionState = ExchangeConnectionState.CONNECTED;
        private boolean reconciliationComplete = true;
        private MarketQualitySnapshot marketQuality = market();
        private boolean accountSupportsProduct = true;
        private boolean permissionsAllowProduct = true;
        private boolean riskAllowed = true;
        private boolean strategyAssigned = true;
        private boolean strategyDataReady = true;
        private StrategySignal latestSignal = signal("BUY");
        private int openOrders = 0;
        private int openPositions = 0;
        private boolean duplicateOrderExists = false;
        private boolean disabledByUser = false;

        ContextBuilder instrument(Instrument value) {
            this.instrument = value;
            return this;
        }

        ContextBuilder policy(SymbolTradingPolicy value) {
            this.policy = value;
            return this;
        }

        ContextBuilder connectionState(ExchangeConnectionState value) {
            this.connectionState = value;
            return this;
        }

        ContextBuilder marketQuality(MarketQualitySnapshot value) {
            this.marketQuality = value;
            return this;
        }

        ContextBuilder riskAllowed(boolean value) {
            this.riskAllowed = value;
            return this;
        }

        ContextBuilder strategyAssigned(boolean value) {
            this.strategyAssigned = value;
            return this;
        }

        ContextBuilder latestSignal(StrategySignal value) {
            this.latestSignal = value;
            return this;
        }

        ContextBuilder duplicateOrderExists(boolean value) {
            this.duplicateOrderExists = value;
            return this;
        }

        TradabilityContext build() {
            return new TradabilityContext(
                    instrument,
                    policy,
                    connectionState,
                    reconciliationComplete,
                    marketQuality,
                    accountSupportsProduct,
                    permissionsAllowProduct,
                    riskAllowed,
                    strategyAssigned,
                    strategyDataReady,
                    null,
                    latestSignal,
                    openOrders,
                    openPositions,
                    duplicateOrderExists,
                    disabledByUser,
                    instrument.metadata(),
                    Instant.now());
        }
    }
}
