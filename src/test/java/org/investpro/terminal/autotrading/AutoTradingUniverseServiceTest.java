package org.investpro.terminal.autotrading;

import org.investpro.terminal.domain.Asset;
import org.investpro.terminal.domain.AssetClass;
import org.investpro.terminal.domain.Instrument;
import org.investpro.terminal.domain.InstrumentId;
import org.investpro.terminal.domain.MarketTick;
import org.investpro.terminal.domain.StrategySignal;
import org.investpro.terminal.domain.TradingStatus;
import org.investpro.terminal.instrument.InstrumentMasterService;
import org.investpro.terminal.provider.AccountProvider;
import org.investpro.terminal.provider.BrokerActivityProvider;
import org.investpro.terminal.provider.HistoricalDataProvider;
import org.investpro.terminal.provider.InstrumentProvider;
import org.investpro.terminal.provider.MarketDataProvider;
import org.investpro.terminal.provider.ProviderBundle;
import org.investpro.terminal.provider.TradingProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoTradingUniverseServiceTest {

    @Test
    void connectedExchangeDiscoversEligibleSymbolsAndSubscribesMarketData() {
        List<Instrument> instruments = List.of(instrument("BTC/USD"), instrument("ETH/USD"));
        List<InstrumentId> subscriptions = new ArrayList<>();

        AutoTradingUniverseService service = service(
                instruments,
                policy(25),
                provider -> true,
                (providerId, instrument) -> subscriptions.add(instrument.id()));
        service.registerExchange(new FakeProvider("TEST", instruments), () -> ExchangeConnectionState.CONNECTED);

        UniverseScanResult result = service.scan("TEST").join();

        assertEquals(2, result.discoveredCount());
        assertEquals(2, result.eligibleCount());
        assertEquals(2, subscriptions.size());
    }

    @Test
    void disconnectedOrReconcilingExchangeDoesNotTrade() {
        List<Instrument> instruments = List.of(instrument("BTC/USD"));

        AutoTradingUniverseService disconnected = service(
                instruments,
                policy(25),
                provider -> true,
                AutoTradingUniverseService.MarketDataSubscriptionGateway.noop());
        disconnected.registerExchange(new FakeProvider("TEST", instruments), () -> ExchangeConnectionState.DISCONNECTED);
        assertEquals(ExchangeConnectionState.DISCONNECTED, disconnected.scan("TEST").join().connectionState());

        AutoTradingUniverseService reconciling = service(
                instruments,
                policy(25),
                provider -> false,
                AutoTradingUniverseService.MarketDataSubscriptionGateway.noop());
        reconciling.registerExchange(new FakeProvider("TEST", instruments), () -> ExchangeConnectionState.CONNECTED);
        assertEquals(ExchangeConnectionState.RECONCILING, reconciling.scan("TEST").join().connectionState());
    }

    @Test
    void maxConcurrentActiveSymbolsIsEnforced() {
        List<Instrument> instruments = List.of(instrument("BTC/USD"), instrument("ETH/USD"), instrument("SOL/USD"));
        AutoTradingUniverseService service = service(
                instruments,
                policy(2),
                provider -> true,
                AutoTradingUniverseService.MarketDataSubscriptionGateway.noop());
        service.registerExchange(new FakeProvider("TEST", instruments), () -> ExchangeConnectionState.CONNECTED);

        UniverseScanResult result = service.scan("TEST").join();

        assertEquals(2, result.eligibleCount());
        assertEquals(1, result.symbols().stream().filter(symbol -> !symbol.tradable()).count());
    }

    private AutoTradingUniverseService service(
            List<Instrument> instruments,
            SymbolTradingPolicy policy,
            AutoTradingUniverseService.ReconciliationGateway reconciliationGateway,
            AutoTradingUniverseService.MarketDataSubscriptionGateway subscriptionGateway
    ) {
        return new AutoTradingUniverseService(
                new InstrumentMasterService(),
                new TradabilityService(),
                policy,
                new SymbolBlocklistService(),
                instrument -> new AutoTradingUniverseService.StrategyAssignment(
                        true,
                        true,
                        null,
                        new StrategySignal(
                                "trend",
                                instrument.id(),
                                "BUY",
                                0.8,
                                "",
                                "",
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ONE,
                                "MARKET",
                                Instant.now())),
                (provider, marketDataProvider, instrument) -> CompletableFuture.completedFuture(
                        new MarketQualitySnapshot(
                                tick(instrument.id()),
                                0.1,
                                90,
                                200000,
                                Instant.now())),
                AutoTradingUniverseService.SymbolRuntimeStateProvider.empty(),
                reconciliationGateway,
                subscriptionGateway,
                AutoTradingUniverseService.AutoTradingDecisionAuditSink.noop());
    }

    private SymbolTradingPolicy policy(int maxActiveSymbols) {
        return new SymbolTradingPolicy(
                true, true, true, 0, maxActiveSymbols,
                Duration.ofSeconds(60), Duration.ofSeconds(300),
                true, true, true, true, false,
                Set.of(), Set.of(AssetClass.CRYPTO),
                100000, 0.30, 60, 1, 1,
                true, true, true, Duration.ofSeconds(15),
                false, true, false, 70, 0.50, true);
    }

    private MarketTick tick(InstrumentId id) {
        return new MarketTick(
                id,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(100.1),
                BigDecimal.valueOf(100.05),
                BigDecimal.valueOf(200000),
                Instant.now());
    }

    private Instrument instrument(String symbol) {
        String[] parts = symbol.split("/", 2);
        return new Instrument(
                new InstrumentId("TEST", symbol, symbol),
                new Asset(parts[0], parts[0], AssetClass.CRYPTO, "", ""),
                new Asset(parts[1], parts[1], AssetClass.CRYPTO, "", ""),
                symbol,
                AssetClass.CRYPTO,
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
                Map.of());
    }

    private record FakeProvider(String providerId, List<Instrument> instruments) implements ProviderBundle, InstrumentProvider {
        @Override
        public Optional<InstrumentProvider> instrumentProvider() {
            return Optional.of(this);
        }

        @Override
        public Optional<MarketDataProvider> marketDataProvider() {
            return Optional.empty();
        }

        @Override
        public Optional<TradingProvider> tradingProvider() {
            return Optional.empty();
        }

        @Override
        public Optional<AccountProvider> accountProvider() {
            return Optional.empty();
        }

        @Override
        public Optional<BrokerActivityProvider> brokerActivityProvider() {
            return Optional.empty();
        }

        @Override
        public Optional<HistoricalDataProvider> historicalDataProvider() {
            return Optional.empty();
        }

        @Override
        public CompletableFuture<List<Instrument>> discoverInstruments() {
            return CompletableFuture.completedFuture(instruments);
        }

        @Override
        public CompletableFuture<Optional<Instrument>> resolveInstrument(String symbol) {
            return CompletableFuture.completedFuture(instruments.stream()
                    .filter(instrument -> instrument.id().symbol().equalsIgnoreCase(symbol))
                    .findFirst());
        }
    }
}
