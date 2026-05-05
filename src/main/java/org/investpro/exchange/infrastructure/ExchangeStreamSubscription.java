package org.investpro.exchange.infrastructure;

import lombok.Getter;
import lombok.Setter;
import org.investpro.models.trading.TradePair;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Defines what the app wants to stream from an exchange.
 *
 * <h2>Subscription Flags (9 total):</h2>
 * <ul>
 *   <li><b>ticker</b> - Real-time price updates (bid/ask/last price)</li>
 *   <li><b>trades</b> - Individual trade executions as they occur</li>
 *   <li><b>candles</b> - Aggregated OHLCV (1-minute candles)</li>
 *   <li><b>orderBook</b> - Order book depth updates</li>
 *   <li><b>account</b> - Account status changes (equity, margin, etc.)</li>
 *   <li><b>orders</b> - Open order updates (new, updated, cancelled)</li>
 *   <li><b>fills</b> - Order execution fills with trade details</li>
 *   <li><b>positions</b> - Position updates (open, modifications, closes)</li>
 *   <li><b>balances</b> - Account balance changes per currency</li>
 * </ul>
 *
 * <h2>Factory Methods (6 total):</h2>
 * <ul>
 *   <li><b>everything(pairs)</b> - Subscribe to all 9 stream types for given pairs</li>
 *   <li><b>marketData(pairs)</b> - Subscribe to market data only (ticker, trades, candles, orderBook)</li>
 *   <li><b>accountData()</b> - Subscribe to account data only (account, orders, fills, positions, balances)</li>
 *   <li><b>forTicker(pairs)</b> - Subscribe to ticker only</li>
 *   <li><b>forTrades(pairs)</b> - Subscribe to trades only</li>
 *   <li><b>forOrderBook(pairs)</b> - Subscribe to order book only</li>
 * </ul>
 *
 * <h2>Usage Examples:</h2>
 * <pre>
 * // Stream everything for BTC/USDT
 * ExchangeStreamSubscription sub = ExchangeStreamSubscription.everything(
 *     Set.of(new TradePair("BTC", "USDT"))
 * );
 *
 * // Stream only market data for multiple pairs
 * ExchangeStreamSubscription sub = ExchangeStreamSubscription.marketData(
 *     Set.of(new TradePair("BTC", "USDT"), new TradePair("ETH", "USDT"))
 * );
 *
 * // Stream only account changes
 * ExchangeStreamSubscription sub = ExchangeStreamSubscription.accountData();
 *
 * // Stream only tickers
 * ExchangeStreamSubscription sub = ExchangeStreamSubscription.forTicker(
 *     Set.of(new TradePair("BTC", "USDT"))
 * );
 *
 * // Custom fine-grained subscription
 * ExchangeStreamSubscription sub = new ExchangeStreamSubscription();
 * sub.setTradePairs(Set.of(new TradePair("BTC", "USDT")));
 * sub.setTicker(true);
 * sub.setOrders(true);
 * sub.setFills(true);
 *
 * // Use subscription
 * exchange.stream(sub, streamConsumer);
 * exchange.stopStreaming(sub);
 * </pre>
 */
@Getter
@Setter
public class ExchangeStreamSubscription {

    private final Set<TradePair> tradePairs = new HashSet<>();

    private boolean ticker;
    private boolean trades;
    private boolean candles;
    private boolean orderBook;
    private boolean account;
    private boolean orders;
    private boolean fills;
    private boolean positions;
    private boolean balances;

    public static ExchangeStreamSubscription everything(Set<TradePair> pairs) {
        ExchangeStreamSubscription subscription = new ExchangeStreamSubscription();
        subscription.tradePairs.addAll(pairs == null ? Collections.emptySet() : pairs);

        subscription.ticker = true;
        subscription.trades = true;
        subscription.candles = true;
        subscription.orderBook = true;
        subscription.account = true;
        subscription.orders = true;
        subscription.fills = true;
        subscription.positions = true;
        subscription.balances = true;

        return subscription;
    }

    public static ExchangeStreamSubscription marketData(Set<TradePair> pairs) {
        ExchangeStreamSubscription subscription = new ExchangeStreamSubscription();
        subscription.tradePairs.addAll(pairs == null ? Collections.emptySet() : pairs);

        subscription.ticker = true;
        subscription.trades = true;
        subscription.candles = true;
        subscription.orderBook = true;


        return subscription;
    }

    public static ExchangeStreamSubscription accountData() {
        ExchangeStreamSubscription subscription = new ExchangeStreamSubscription();

        subscription.account = true;
        subscription.orders = true;
        subscription.fills = true;
        subscription.positions = true;
        subscription.balances = true;

        return subscription;
    }

    public static ExchangeStreamSubscription forTicker(Set<TradePair> pairs) {
        ExchangeStreamSubscription subscription = new ExchangeStreamSubscription();
        subscription.tradePairs.addAll(pairs == null ? Collections.emptySet() : pairs);
        subscription.ticker = true;
        return subscription;
    }

    public static ExchangeStreamSubscription forTrades(Set<TradePair> pairs) {
        ExchangeStreamSubscription subscription = new ExchangeStreamSubscription();
        subscription.tradePairs.addAll(pairs == null ? Collections.emptySet() : pairs);
        subscription.trades = true;
        return subscription;
    }

    public static ExchangeStreamSubscription forOrderBook(Set<TradePair> pairs) {
        ExchangeStreamSubscription subscription = new ExchangeStreamSubscription();
        subscription.tradePairs.addAll(pairs == null ? Collections.emptySet() : pairs);
        subscription.orderBook = true;
        return subscription;
    }

    public Set<TradePair> getTradePairs() {
        return Collections.unmodifiableSet(tradePairs);
    }

    @Override
    public String toString() {
        return "ExchangeStreamSubscription{" +
                "tradePairs=" + tradePairs +
                ", ticker=" + ticker +
                ", trades=" + trades +
                ", candles=" + candles +
                ", orderBook=" + orderBook +
                ", account=" + account +
                ", orders=" + orders +
                ", fills=" + fills +
                ", positions=" + positions +
                ", balances=" + balances +
                '}';
    }

    public void setTradePairs(Set<TradePair> tradePair) {
        this.tradePairs.clear();
        this.tradePairs.addAll(tradePair);
    }
}
