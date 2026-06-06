package org.investpro.terminal.adapter;

import org.investpro.exchange.Exchange;
import org.investpro.models.Account;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.TradePair;
import org.investpro.terminal.domain.AccountSnapshot;
import org.investpro.terminal.domain.Asset;
import org.investpro.terminal.domain.AssetClass;
import org.investpro.terminal.domain.Balance;
import org.investpro.terminal.domain.Candle;
import org.investpro.terminal.domain.ExecutionPlan;
import org.investpro.terminal.domain.Instrument;
import org.investpro.terminal.domain.InstrumentId;
import org.investpro.terminal.domain.MarketTick;
import org.investpro.terminal.domain.OrderBookSnapshot;
import org.investpro.terminal.domain.OrderId;
import org.investpro.terminal.domain.OrderRequest;
import org.investpro.terminal.domain.OrderState;
import org.investpro.terminal.domain.Position;
import org.investpro.terminal.domain.RiskDecision;
import org.investpro.terminal.domain.TradingStatus;
import org.investpro.terminal.provider.AccountProvider;
import org.investpro.terminal.provider.HistoricalDataProvider;
import org.investpro.terminal.provider.InstrumentProvider;
import org.investpro.terminal.provider.MarketDataProvider;
import org.investpro.terminal.provider.ProviderBundle;
import org.investpro.terminal.provider.TradingProvider;
import org.investpro.utils.Side;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class ExchangeTerminalProviderAdapter implements
        ProviderBundle,
        MarketDataProvider,
        HistoricalDataProvider,
        TradingProvider,
        AccountProvider,
        InstrumentProvider {

    protected final Exchange exchange;

    public ExchangeTerminalProviderAdapter(Exchange exchange) {
        if (exchange == null) {
            throw new IllegalArgumentException("exchange is required");
        }
        this.exchange = exchange;
    }

    public Exchange exchange() {
        return exchange;
    }

    @Override
    public String providerId() {
        return safe(exchange.getExchangeId(), exchange.getName());
    }

    @Override
    public Set<AssetClass> supportedAssetClasses() {
        return Set.of();
    }

    @Override
    public boolean supportsLiveMarketData() {
        return true;
    }

    @Override
    public boolean supportsHistoricalCandles() {
        return true;
    }

    @Override
    public boolean supportsTrading() {
        return exchange.canSubmitOrders();
    }

    @Override
    public boolean supportsOrderBook() {
        return exchange.supportsOrderBook();
    }

    @Override
    public boolean supportsAccountBalances() {
        return true;
    }

    @Override
    public boolean supportsPositions() {
        return exchange.supportsPositions();
    }

    @Override
    public boolean supportsInstrumentDiscovery() {
        return true;
    }

    @Override
    public Optional<MarketDataProvider> marketDataProvider() {
        return Optional.of(this);
    }

    @Override
    public Optional<TradingProvider> tradingProvider() {
        return Optional.of(this);
    }

    @Override
    public Optional<AccountProvider> accountProvider() {
        return Optional.of(this);
    }

    @Override
    public Optional<InstrumentProvider> instrumentProvider() {
        return Optional.of(this);
    }

    @Override
    public Optional<HistoricalDataProvider> historicalDataProvider() {
        return Optional.of(this);
    }

    @Override
    public CompletableFuture<MarketTick> latestTick(InstrumentId instrumentId) {
        TradePair pair = TerminalExchangeMapper.tradePairFromInstrument(instrumentId);
        return exchange.fetchTicker(pair)
                .thenApply(ticker -> TerminalExchangeMapper.marketTick(providerId(), pair, ticker));
    }

    @Override
    public CompletableFuture<OrderBookSnapshot> orderBook(InstrumentId instrumentId) {
        TradePair pair = TerminalExchangeMapper.tradePairFromInstrument(instrumentId);
        return exchange.fetchOrderBook(pair)
                .thenApply(orderBook -> TerminalExchangeMapper.orderBook(providerId(), pair, orderBook));
    }

    @Override
    public CompletableFuture<List<Candle>> candles(
            InstrumentId instrumentId,
            String timeframe,
            Instant from,
            Instant to,
            int limit
    ) {
        TradePair pair = TerminalExchangeMapper.tradePairFromInstrument(instrumentId);
        int seconds = timeframeSeconds(timeframe);
        Instant lowerBound = from == null ? Instant.EPOCH : from;
        Instant upperBound = to == null ? Instant.now() : to;
        int effectiveLimit = limit <= 0 ? Integer.MAX_VALUE : limit;

        return CompletableFuture.supplyAsync(() -> {
            try {
                return exchange.getCandleDataSupplier(seconds, pair)
                        .getCandleData()
                        .stream()
                        .filter(candle -> {
                            Instant openTime = candle.timestamp();
                            return !openTime.isBefore(lowerBound) && !openTime.isAfter(upperBound);
                        })
                        .limit(effectiveLimit)
                        .map(candle -> TerminalExchangeMapper.candle(providerId(), pair, timeframe, candle))
                        .toList();
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        });
    }

    @Override
    public CompletableFuture<OrderId> submitOrder(ExecutionPlan executionPlan) {
        if (executionPlan == null || executionPlan.orderRequest() == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("execution plan and order request are required"));
        }
        RiskDecision riskDecision = executionPlan.riskDecision();
        if (riskDecision == null || !riskDecision.allowed()) {
            String reason = riskDecision == null ? "Missing terminal risk decision" : riskDecision.reason();
            return CompletableFuture.failedFuture(new IllegalStateException("Order blocked: " + reason));
        }

        OrderRequest request = executionPlan.orderRequest();
        TradePair pair = TerminalExchangeMapper.tradePairFromInstrument(request.instrumentId());
        Side side = side(request.side());
        double quantity = TerminalExchangeMapper.doubleValue(request.quantity());

        CompletableFuture<String> submittedOrder = "LIMIT".equalsIgnoreCase(request.orderType())
                ? exchange.createLimitOrder(pair, side, quantity, TerminalExchangeMapper.doubleValue(request.limitPrice()))
                : exchange.createMarketOrder(pair, side, quantity);

        return submittedOrder.thenApply(externalId -> new OrderId(
                providerId(),
                "",
                request.clientOrderId(),
                externalId));
    }

    @Override
    public CompletableFuture<OrderState> orderStatus(OrderId orderId) {
        if (orderId == null) {
            return CompletableFuture.completedFuture(OrderState.UNKNOWN);
        }
        return exchange.fetchOpenOrders(null)
                .thenApply(openOrders -> openOrders == null ? List.<OpenOrder>of() : openOrders)
                .thenApply(openOrders -> openOrders.stream()
                        .filter(order -> TerminalExchangeMapper.sameOrder(
                                order,
                                orderId.externalOrderId(),
                                orderId.clientOrderId()))
                        .findFirst()
                        .map(TerminalExchangeMapper::orderState)
                        .orElse(OrderState.UNKNOWN))
                .exceptionally(ignored -> OrderState.UNKNOWN);
    }

    @Override
    public CompletableFuture<OrderId> previewOrder(OrderRequest request) {
        if (request == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("order request is required"));
        }
        return CompletableFuture.completedFuture(new OrderId(providerId(), "", request.clientOrderId(), ""));
    }

    @Override
    public CompletableFuture<AccountSnapshot> accountSnapshot(String accountId) {
        return exchange.fetchAccount()
                .thenCompose(account -> {
                    String resolvedAccountId = TerminalExchangeMapper.accountId(account, accountId, providerId());
                    return positions()
                            .exceptionally(ignored -> List.of())
                            .thenApply(positions -> new AccountSnapshot(
                                    providerId(),
                                    resolvedAccountId,
                                    balances(account, resolvedAccountId),
                                    positions,
                                    account == null || account.getUpdatedAt() == null ? Instant.now() : account.getUpdatedAt()));
                });
    }

    @Override
    public CompletableFuture<List<Instrument>> discoverInstruments() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return exchange.getTradablePairs()
                        .stream()
                        .map(this::instrumentForPair)
                        .toList();
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<Instrument>> resolveInstrument(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        TradePair requested = TerminalExchangeMapper.tradePairFromInstrument(new InstrumentId(providerId(), symbol, symbol));
        return discoverInstruments()
                .thenApply(instruments -> instruments.stream()
                        .filter(instrument -> sameSymbol(instrument, requested))
                        .findFirst());
    }

    protected Instrument instrumentForPair(TradePair pair) {
        AssetClass assetClass = TerminalExchangeMapper.inferAssetClass(pair, providerId());
        Asset baseAsset = TerminalExchangeMapper.asset(pair.getBaseCode(), assetClass);
        Asset quoteAsset = TerminalExchangeMapper.asset(pair.getCounterCode(), assetClass);
        return new Instrument(
                TerminalExchangeMapper.instrumentId(providerId(), pair),
                baseAsset,
                quoteAsset,
                pair.toSlashSymbol(),
                assetClass,
                safe(exchange.getDisplayName(), exchange.getName()),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                tradingStatus(pair),
                false,
                false,
                true,
                true,
                instrumentMetadata(pair));
    }

    protected Map<String, Object> instrumentMetadata(TradePair pair) {
        return TerminalExchangeMapper.baseMetadata(pair, providerId());
    }

    protected TradingStatus tradingStatus(TradePair pair) {
        try {
            return exchange.supportsTradePair(pair) ? TradingStatus.ACTIVE : TradingStatus.DISABLED;
        } catch (Exception exception) {
            return TradingStatus.UNKNOWN;
        }
    }

    protected CompletableFuture<List<Position>> positions() {
        if (!exchange.supportsPositions()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return exchange.fetchAllPositions()
                .thenApply(positions -> positions == null ? List.<org.investpro.models.trading.Position>of() : positions)
                .thenApply(positions -> positions.stream()
                        .map(position -> TerminalExchangeMapper.position(providerId(), position))
                        .toList());
    }

    protected List<Balance> balances(Account account, String accountId) {
        if (account == null) {
            return List.of();
        }

        Map<String, Double> totals = new LinkedHashMap<>(account.getBalances());
        Map<String, Double> available = account.getAvailableBalances();
        Map<String, Double> locked = account.getLockedBalances();

        if (totals.isEmpty() && account.getTotalBalance() > 0) {
            totals.put(account.getBaseCurrency(), account.getTotalBalance());
        }

        List<Balance> result = new ArrayList<>();
        for (Map.Entry<String, Double> entry : totals.entrySet()) {
            String code = entry.getKey();
            double total = entry.getValue() == null ? 0.0 : entry.getValue();
            double availableAmount = available.getOrDefault(code, account.getAvailableBalance());
            double lockedAmount = locked.getOrDefault(code, Math.max(0.0, total - availableAmount));
            result.add(TerminalExchangeMapper.balance(accountId, code, total, availableAmount, lockedAmount));
        }
        return result;
    }

    protected int timeframeSeconds(String timeframe) {
        String normalized = timeframe == null ? "" : timeframe.trim().toUpperCase();
        return switch (normalized) {
            case "M1", "1M", "1MIN" -> 60;
            case "M3", "3M", "3MIN" -> 180;
            case "M5", "5M", "5MIN" -> 300;
            case "M15", "15M", "15MIN" -> 900;
            case "M30", "30M", "30MIN" -> 1800;
            case "H1", "1H", "60M" -> 3600;
            case "H2", "2H" -> 7200;
            case "H4", "4H" -> 14400;
            case "H6", "6H" -> 21600;
            case "H12", "12H" -> 43200;
            case "D1", "1D" -> 86400;
            default -> 3600;
        };
    }

    private boolean sameSymbol(Instrument instrument, TradePair requested) {
        return instrument != null
                && requested != null
                && instrument.id().symbol().equalsIgnoreCase(requested.toSlashSymbol());
    }

    private Side side(String value) {
        return "SELL".equalsIgnoreCase(value) ? Side.SELL : Side.BUY;
    }

    private String safe(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }
}
