package org.investpro.terminal.autotrading;

import org.investpro.terminal.domain.Instrument;
import org.investpro.terminal.domain.InstrumentId;
import org.investpro.terminal.domain.MarketTick;
import org.investpro.terminal.domain.StrategyScore;
import org.investpro.terminal.domain.StrategySignal;
import org.investpro.terminal.instrument.InstrumentMasterService;
import org.investpro.terminal.provider.InstrumentProvider;
import org.investpro.terminal.provider.MarketDataProvider;
import org.investpro.terminal.provider.ProviderBundle;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class AutoTradingUniverseService {

    private final InstrumentMasterService instrumentMasterService;
    private final TradabilityService tradabilityService;
    private final SymbolTradingPolicy policy;
    private final SymbolBlocklistService blocklistService;
    private final StrategyAssignmentGateway strategyAssignmentGateway;
    private final MarketQualityProvider marketQualityProvider;
    private final SymbolRuntimeStateProvider runtimeStateProvider;
    private final ReconciliationGateway reconciliationGateway;
    private final MarketDataSubscriptionGateway marketDataSubscriptionGateway;
    private final AutoTradingDecisionAuditSink auditSink;
    private final Map<String, RegisteredExchange> exchanges = new LinkedHashMap<>();
    private final Map<String, SymbolEligibility> latestEligibility = new LinkedHashMap<>();

    public AutoTradingUniverseService(
            InstrumentMasterService instrumentMasterService,
            TradabilityService tradabilityService,
            SymbolTradingPolicy policy,
            SymbolBlocklistService blocklistService,
            StrategyAssignmentGateway strategyAssignmentGateway,
            MarketQualityProvider marketQualityProvider,
            SymbolRuntimeStateProvider runtimeStateProvider,
            ReconciliationGateway reconciliationGateway,
            MarketDataSubscriptionGateway marketDataSubscriptionGateway,
            AutoTradingDecisionAuditSink auditSink
    ) {
        this.instrumentMasterService = instrumentMasterService == null
                ? new InstrumentMasterService()
                : instrumentMasterService;
        this.tradabilityService = tradabilityService == null ? new TradabilityService() : tradabilityService;
        this.policy = policy == null ? SymbolTradingPolicy.defaults() : policy;
        this.blocklistService = blocklistService == null
                ? new SymbolBlocklistService(this.policy.blockedSymbols())
                : blocklistService;
        this.strategyAssignmentGateway = strategyAssignmentGateway == null
                ? StrategyAssignmentGateway.none()
                : strategyAssignmentGateway;
        this.marketQualityProvider = marketQualityProvider == null
                ? MarketQualityProvider.fromProvider()
                : marketQualityProvider;
        this.runtimeStateProvider = runtimeStateProvider == null
                ? SymbolRuntimeStateProvider.empty()
                : runtimeStateProvider;
        this.reconciliationGateway = reconciliationGateway == null
                ? ReconciliationGateway.alwaysComplete()
                : reconciliationGateway;
        this.marketDataSubscriptionGateway = marketDataSubscriptionGateway == null
                ? MarketDataSubscriptionGateway.noop()
                : marketDataSubscriptionGateway;
        this.auditSink = auditSink == null ? AutoTradingDecisionAuditSink.noop() : auditSink;
    }

    public void registerExchange(
            ProviderBundle provider,
            Supplier<ExchangeConnectionState> connectionStateSupplier
    ) {
        if (provider == null) {
            return;
        }
        exchanges.put(provider.providerId(), new RegisteredExchange(provider, connectionStateSupplier));
    }

    public CompletableFuture<List<UniverseScanResult>> scanAll() {
        List<CompletableFuture<UniverseScanResult>> scans = exchanges.values()
                .stream()
                .map(this::scan)
                .toList();
        return CompletableFuture.allOf(scans.toArray(CompletableFuture[]::new))
                .thenApply(unused -> scans.stream().map(CompletableFuture::join).toList());
    }

    public CompletableFuture<UniverseScanResult> scan(String providerId) {
        RegisteredExchange exchange = exchanges.get(providerId);
        if (exchange == null) {
            return CompletableFuture.completedFuture(new UniverseScanResult(
                    providerId,
                    ExchangeConnectionState.DISCONNECTED,
                    0,
                    0,
                    0,
                    List.of(),
                    Instant.now(),
                    Map.of("reason", "Provider not registered")));
        }
        return scan(exchange);
    }

    public Map<String, SymbolEligibility> latestEligibilitySnapshot() {
        return Map.copyOf(latestEligibility);
    }

    public SymbolBlocklistService blocklistService() {
        return blocklistService;
    }

    private CompletableFuture<UniverseScanResult> scan(RegisteredExchange registeredExchange) {
        ProviderBundle provider = registeredExchange.provider();
        ExchangeConnectionState connectionState = registeredExchange.connectionState();
        boolean reconciliationComplete = reconciliationGateway.reconciliationComplete(provider.providerId());

        if (connectionState != ExchangeConnectionState.CONNECTED) {
            UniverseScanResult paused = new UniverseScanResult(
                    provider.providerId(),
                    connectionState,
                    0,
                    0,
                    0,
                    List.of(),
                    Instant.now(),
                    Map.of("paused", true, "reason", "exchange is not connected"));
            auditSink.record(paused);
            return CompletableFuture.completedFuture(paused);
        }

        if (policy.waitForReconciliationBeforeTrading() && !reconciliationComplete) {
            UniverseScanResult paused = new UniverseScanResult(
                    provider.providerId(),
                    ExchangeConnectionState.RECONCILING,
                    0,
                    0,
                    0,
                    List.of(),
                    Instant.now(),
                    Map.of("paused", true, "reason", "reconciliation required"));
            auditSink.record(paused);
            return CompletableFuture.completedFuture(paused);
        }

        Optional<InstrumentProvider> instrumentProvider = provider.instrumentProvider();
        if (instrumentProvider.isEmpty()) {
            UniverseScanResult result = new UniverseScanResult(
                    provider.providerId(),
                    connectionState,
                    0,
                    0,
                    0,
                    List.of(),
                    Instant.now(),
                    Map.of("reason", "provider has no instrument discovery"));
            auditSink.record(result);
            return CompletableFuture.completedFuture(result);
        }

        return instrumentProvider.get().discoverInstruments()
                .thenCompose(instruments -> evaluateInstruments(provider, connectionState, reconciliationComplete, instruments));
    }

    private CompletableFuture<UniverseScanResult> evaluateInstruments(
            ProviderBundle provider,
            ExchangeConnectionState connectionState,
            boolean reconciliationComplete,
            List<Instrument> discovered
    ) {
        List<Instrument> instruments = trimToMaxSymbols(discovered);
        instrumentMasterService.registerAll(instruments);
        Optional<MarketDataProvider> marketDataProvider = provider.marketDataProvider();

        List<CompletableFuture<SymbolEligibility>> evaluations = instruments.stream()
                .map(instrument -> evaluateInstrument(
                        provider,
                        marketDataProvider.orElse(null),
                        connectionState,
                        reconciliationComplete,
                        instrument))
                .toList();

        return CompletableFuture.allOf(evaluations.toArray(CompletableFuture[]::new))
                .thenApply(unused -> {
                    List<SymbolEligibility> symbols = evaluations.stream()
                            .map(CompletableFuture::join)
                            .sorted(Comparator.comparing(symbol -> symbol.instrument().id().symbol()))
                            .toList();
                    symbols = enforceMaxConcurrentActiveSymbols(symbols);
                    int eligible = (int) symbols.stream().filter(SymbolEligibility::tradable).count();
                    UniverseScanResult result = new UniverseScanResult(
                            provider.providerId(),
                            connectionState,
                            discovered == null ? 0 : discovered.size(),
                            eligible,
                            symbols.size() - eligible,
                            symbols,
                            Instant.now(),
                            Map.of("reconciliationComplete", reconciliationComplete));
                    symbols.forEach(symbol -> latestEligibility.put(symbol.instrument().id().key(), symbol));
                    auditSink.record(result);
                    return result;
                });
    }

    private CompletableFuture<SymbolEligibility> evaluateInstrument(
            ProviderBundle provider,
            MarketDataProvider marketDataProvider,
            ExchangeConnectionState connectionState,
            boolean reconciliationComplete,
            Instrument instrument
    ) {
        StrategyAssignment assignment = strategyAssignmentGateway.assignmentFor(instrument);
        SymbolRuntimeState runtimeState = runtimeStateProvider.stateFor(instrument.id());
        boolean disabled = runtimeState.disabledByUser() || blocklistService.isBlocked(instrument.id());

        return marketQualityProvider.quality(provider, marketDataProvider, instrument)
                .exceptionally(ignored -> MarketQualitySnapshot.missing())
                .thenApply(marketQuality -> {
                    TradabilityContext context = new TradabilityContext(
                            instrument,
                            policy,
                            connectionState,
                            reconciliationComplete,
                            marketQuality,
                            runtimeState.accountSupportsProduct(),
                            runtimeState.permissionsAllowProduct(),
                            runtimeState.riskAllowed(),
                            assignment.assigned(),
                            assignment.dataReady(),
                            assignment.score(),
                            assignment.signal(),
                            runtimeState.openOrders(),
                            runtimeState.openPositions(),
                            runtimeState.duplicateOrderExists(),
                            disabled,
                            instrument.metadata(),
                            Instant.now());
                    TradabilityResult result = tradabilityService.evaluate(context);
                    SymbolEligibility eligibility = SymbolEligibility.fromResult(context, result);
                    if (eligibility.tradable() && policy.subscribeMarketDataForEligibleSymbols()) {
                        marketDataSubscriptionGateway.subscribe(provider.providerId(), instrument);
                    }
                    auditSink.record(eligibility);
                    return eligibility;
                });
    }

    private List<Instrument> trimToMaxSymbols(List<Instrument> discovered) {
        if (discovered == null || discovered.isEmpty()) {
            return List.of();
        }
        int max = policy.maxSymbolsPerExchange();
        if (max <= 0 || discovered.size() <= max) {
            return List.copyOf(discovered);
        }
        return discovered.stream().limit(max).toList();
    }

    private List<SymbolEligibility> enforceMaxConcurrentActiveSymbols(List<SymbolEligibility> symbols) {
        int max = policy.maxConcurrentActiveSymbols();
        if (max <= 0) {
            return symbols;
        }
        int active = 0;
        List<SymbolEligibility> replacements = new ArrayList<>();
        for (SymbolEligibility symbol : symbols) {
            if (!symbol.tradable()) {
                replacements.add(symbol);
                continue;
            }
            active++;
            if (active <= max) {
                replacements.add(symbol);
                continue;
            }
            SymbolEligibility limited = new SymbolEligibility(
                    symbol.instrument(),
                    false,
                    TradabilityFailureReason.UNKNOWN,
                    AutoTradeSymbolState.PAUSED,
                    symbol.assignedStrategy(),
                    symbol.strategyScore(),
                    symbol.latestSignal(),
                    symbol.spreadPercent(),
                    symbol.liquidityScore(),
                    symbol.volume24h(),
                    symbol.marketDataStatus(),
                    symbol.openOrders(),
                    symbol.openPositions(),
                    Instant.now(),
                    Map.of("reason", "max concurrent active symbols reached"));
            replacements.add(limited);
            latestEligibility.put(limited.instrument().id().key(), limited);
        }
        return replacements;
    }

    private record RegisteredExchange(
            ProviderBundle provider,
            Supplier<ExchangeConnectionState> connectionStateSupplier
    ) {
        ExchangeConnectionState connectionState() {
            return connectionStateSupplier == null ? ExchangeConnectionState.CONNECTED : connectionStateSupplier.get();
        }
    }

    public record StrategyAssignment(
            boolean assigned,
            boolean dataReady,
            StrategyScore score,
            StrategySignal signal
    ) {
        public static StrategyAssignment none() {
            return new StrategyAssignment(false, false, null, null);
        }
    }

    @FunctionalInterface
    public interface StrategyAssignmentGateway {
        StrategyAssignment assignmentFor(Instrument instrument);

        static StrategyAssignmentGateway none() {
            return instrument -> StrategyAssignment.none();
        }
    }

    @FunctionalInterface
    public interface MarketQualityProvider {
        CompletableFuture<MarketQualitySnapshot> quality(
                ProviderBundle provider,
                MarketDataProvider marketDataProvider,
                Instrument instrument
        );

        static MarketQualityProvider fromProvider() {
            return (provider, marketDataProvider, instrument) -> {
                if (marketDataProvider == null || instrument == null) {
                    return CompletableFuture.completedFuture(MarketQualitySnapshot.missing());
                }
                return marketDataProvider.latestTick(instrument.id())
                        .thenApply(AutoTradingUniverseService::qualityFromTick);
            };
        }
    }

    @FunctionalInterface
    public interface ReconciliationGateway {
        boolean reconciliationComplete(String providerId);

        static ReconciliationGateway alwaysComplete() {
            return ignored -> true;
        }
    }

    @FunctionalInterface
    public interface MarketDataSubscriptionGateway {
        void subscribe(String providerId, Instrument instrument);

        static MarketDataSubscriptionGateway noop() {
            return (providerId, instrument) -> { };
        }
    }

    @FunctionalInterface
    public interface AutoTradingDecisionAuditSink {
        void record(Object decision);

        static AutoTradingDecisionAuditSink noop() {
            return ignored -> { };
        }
    }

    @FunctionalInterface
    public interface SymbolRuntimeStateProvider {
        SymbolRuntimeState stateFor(InstrumentId instrumentId);

        static SymbolRuntimeStateProvider empty() {
            return ignored -> SymbolRuntimeState.defaults();
        }
    }

    public record SymbolRuntimeState(
            boolean accountSupportsProduct,
            boolean permissionsAllowProduct,
            boolean riskAllowed,
            int openOrders,
            int openPositions,
            boolean duplicateOrderExists,
            boolean disabledByUser
    ) {
        public SymbolRuntimeState {
            openOrders = Math.max(0, openOrders);
            openPositions = Math.max(0, openPositions);
        }

        public static SymbolRuntimeState defaults() {
            return new SymbolRuntimeState(true, true, true, 0, 0, false, false);
        }
    }

    private static MarketQualitySnapshot qualityFromTick(MarketTick tick) {
        if (tick == null) {
            return MarketQualitySnapshot.missing();
        }
        double bid = tick.bid().doubleValue();
        double ask = tick.ask().doubleValue();
        double spread = Double.POSITIVE_INFINITY;
        if (bid > 0 && ask > 0) {
            double mid = (bid + ask) / 2.0;
            spread = mid > 0 ? Math.abs(ask - bid) / mid * 100.0 : Double.POSITIVE_INFINITY;
        }
        int liquidity = tick.volume().doubleValue() > 0 ? 75 : 0;
        return new MarketQualitySnapshot(tick, spread, liquidity, tick.volume().doubleValue(), tick.timestamp());
    }
}
