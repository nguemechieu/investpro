package org.investpro.core.state;

import org.investpro.models.Account;
import org.investpro.models.trading.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Read-only financial snapshots for presentation, never a replacement for execution-time validation. */
public final class SystemStateStore {
    private static final SystemStateStore INSTANCE = new SystemStateStore();
    public static SystemStateStore getInstance() { return INSTANCE; }
    public record Key(String exchange, String symbol) {}
    public record Quote(double last, double bid, double ask, double open, double high, double low,
                        double volume, long timestamp, Ticker.QuoteType type) {
        public Ticker ticker() {
            Ticker result = new Ticker(last, bid, ask, open, high, low, volume, timestamp);
            result.setQuoteType(type); return result;
        }
    }
    public record AccountView(double balance, double available, double equity, double marginUsed,
                              double freeMargin, String currency, Instant receivedAt) {}
    public record PositionView(String id, String symbol, String side, double quantity, double entry,
                               double current, double unrealizedPnl, boolean open) {}
    public record OrderView(String id, String symbol, String side, String status, double price, double size) {}
    public record Level(double price, double size, int orders) {}
    public record Book(List<Level> bids, List<Level> asks, Instant timestamp, String sequence) {
        public OrderBook orderBook(TradePair pair) {
            var value = new OrderBook(pair,
                    bids.stream().map(l -> new OrderBook.PriceLevel(l.price(), l.size(), l.orders())).toList(),
                    asks.stream().map(l -> new OrderBook.PriceLevel(l.price(), l.size(), l.orders())).toList());
            value.setTimestamp(timestamp); value.setSequence(sequence); return value;
        }
    }
    public record Health(boolean connected, String status, Instant receivedAt) {}
    public record StrategyView(String name, String state, Instant receivedAt) {}
    private final Map<Key, Quote> quotes = new ConcurrentHashMap<>();
    private final Map<Key, Book> books = new ConcurrentHashMap<>();
    private final Map<String, AccountView> accounts = new ConcurrentHashMap<>();
    private final Map<String, List<PositionView>> positions = new ConcurrentHashMap<>();
    private final Map<String, List<OrderView>> orders = new ConcurrentHashMap<>();
    private final Map<String, Health> health = new ConcurrentHashMap<>();
    private final Map<Key, StrategyView> strategies = new ConcurrentHashMap<>();

    private static String venue(String exchange) { return Objects.requireNonNullElse(exchange, ""); }
    public Key key(String exchange, TradePair pair) {
        return new Key(Objects.requireNonNullElse(exchange, ""), pair == null ? "" : pair.toString('/'));
    }
    public Quote updateTicker(String exchange, TradePair pair, Ticker ticker) {
        Quote value = new Quote(ticker.getLastPrice(), ticker.getBidPrice(), ticker.getAskPrice(),
                ticker.getOpenPrice(), ticker.getHighPrice(), ticker.getLowPrice(), ticker.getVolume(),
                ticker.getTimestamp(), ticker.getQuoteType());
        return quotes.compute(key(exchange, pair), (_, previous) ->
                previous != null && previous.timestamp() > value.timestamp() ? previous : value);
    }
    public Optional<Quote> ticker(String exchange, TradePair pair) { return Optional.ofNullable(quotes.get(key(exchange, pair))); }
    public Map<Key, Quote> tickers() { return Map.copyOf(quotes); }
    public void updateAccount(String exchange, Account value) {
        accounts.put(venue(exchange), new AccountView(value.getTotalBalance(), value.getAvailableBalance(), value.getEquity(),
                value.getMarginUsed(), value.getFreeMargin(), value.getBaseCurrency(), Instant.now()));
    }
    public Optional<AccountView> account(String exchange) { return Optional.ofNullable(accounts.get(venue(exchange))); }
    public void updatePositions(String exchange, List<Position> values) {
        positions.put(venue(exchange), values.stream().filter(Objects::nonNull).map(p -> new PositionView(p.getPositionId(),
                p.getTradePair() == null ? "" : p.getTradePair().toString('/'), String.valueOf(p.getSide()),
                p.getQuantity(), p.getEntryPrice(), p.getCurrentPrice(), p.getUnrealizedPnl(), p.isOpen())).toList());
    }
    public List<PositionView> positions(String exchange) { return positions.getOrDefault(venue(exchange), List.of()); }
    public void updateOrders(String exchange, List<OpenOrder> values) {
        orders.put(venue(exchange), values.stream().filter(Objects::nonNull).map(o -> new OrderView(o.getOrderId(),
                o.getTradePair() == null ? "" : o.getTradePair().toString('/'), String.valueOf(o.getSide()),
                String.valueOf(o.getStatus()), o.getPrice(), o.getSize())).toList());
    }
    public List<OrderView> orders(String exchange) { return orders.getOrDefault(venue(exchange), List.of()); }
    public void updateBook(String exchange, TradePair pair, OrderBook book) {
        books.put(key(exchange, pair), new Book(levels(book.getBids()), levels(book.getAsks()), book.getTimestamp(), book.getSequence()));
    }
    private static List<Level> levels(List<OrderBook.PriceLevel> levels) {
        return levels == null ? List.of() : levels.stream().filter(Objects::nonNull)
                .map(l -> new Level(l.getPrice(), l.getSize(), l.getNumOrders())).toList();
    }
    public Optional<Book> book(String exchange, TradePair pair) { return Optional.ofNullable(books.get(key(exchange, pair))); }
    public void updateHealth(String exchange, boolean connected, String status) {
        health.put(venue(exchange), new Health(connected, status, Instant.now()));
    }
    public Map<String, Health> health() { return Map.copyOf(health); }
    public void updateStrategy(String exchange, TradePair pair, String name, String state) {
        strategies.put(key(exchange, pair), new StrategyView(name, state, Instant.now()));
    }
    public Map<Key, StrategyView> strategies() { return Map.copyOf(strategies); }
    public void clearExchange(String exchange) {
        quotes.keySet().removeIf(k -> k.exchange().equals(venue(exchange)));
        books.keySet().removeIf(k -> k.exchange().equals(venue(exchange)));
        strategies.keySet().removeIf(k -> k.exchange().equals(venue(exchange)));
        accounts.remove(venue(exchange)); positions.remove(venue(exchange)); orders.remove(venue(exchange)); health.remove(venue(exchange));
    }
}
