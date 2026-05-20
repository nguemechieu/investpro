package org.investpro.exchange.coinbase;

import org.investpro.market.MarketDataCache;
import org.investpro.models.trading.MarketQuote;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coinbase market-data aggregation service backed by {@link MarketDataCache}.
 *
 * <p>This service keeps the latest ticker/order-book/trade view per pair and
 * mirrors quote/order-book updates into the shared market cache.</p>
 */
public class CoinbaseMarketDataService {

	private final MarketDataCache marketDataCache;
	private final Map<String, TickerEnvelope> latestTickerByPair = new ConcurrentHashMap<>();
	private final Map<String, OrderBookEnvelope> latestOrderBookByPair = new ConcurrentHashMap<>();
	private final Map<String, List<Trade>> recentTradesByPair = new ConcurrentHashMap<>();

	public CoinbaseMarketDataService(MarketDataCache marketDataCache) {
		this.marketDataCache = Objects.requireNonNull(marketDataCache, "marketDataCache must not be null");
	}

	public void onTickerUpdate(TradePair tradePair, Ticker ticker) {
		if (tradePair == null || ticker == null) {
			return;
		}

		String key = pairKey(tradePair);
		latestTickerByPair.put(key, new TickerEnvelope(ticker, Instant.now()));
		marketDataCache.updateQuote(tradePair, toMarketQuote(tradePair, ticker));
	}

	public Optional<Ticker> getTickerFromCache(TradePair tradePair, Duration maxAge) {
		if (tradePair == null) {
			return Optional.empty();
		}

		String key = pairKey(tradePair);
		TickerEnvelope envelope = latestTickerByPair.get(key);
		if (envelope != null && isFresh(envelope.updatedAt(), maxAge)) {
			return Optional.of(envelope.ticker());
		}

		Optional<MarketQuote> quote = marketDataCache.getQuote(tradePair)
				.filter(marketQuote -> maxAge == null || marketQuote.isFresh(maxAge));

		return quote.map(CoinbaseMarketDataService::toTicker);
	}

	public void onOrderBookUpdate(TradePair tradePair, OrderBook orderBook) {
		if (tradePair == null || orderBook == null) {
			return;
		}

		String key = pairKey(tradePair);
		latestOrderBookByPair.put(key, new OrderBookEnvelope(orderBook, Instant.now()));
		marketDataCache.updateOrderBook(tradePair, orderBook);
	}

	public Optional<OrderBook> getOrderBookFromCache(TradePair tradePair, Duration maxAge) {
		if (tradePair == null) {
			return Optional.empty();
		}

		String key = pairKey(tradePair);
		OrderBookEnvelope envelope = latestOrderBookByPair.get(key);
		if (envelope != null && isFresh(envelope.updatedAt(), maxAge)) {
			return Optional.of(envelope.orderBook());
		}

		Optional<OrderBook> fromSharedCache = marketDataCache.getOrderBook(tradePair);
		if (fromSharedCache.isEmpty()) {
			return Optional.empty();
		}

		if (maxAge == null || maxAge.isZero() || maxAge.isNegative()) {
			return fromSharedCache;
		}

		Instant timestamp = fromSharedCache.get().getTimestamp();
		if (timestamp == null) {
			return Optional.empty();
		}

		boolean fresh = timestamp.isAfter(Instant.now().minus(maxAge));
		return fresh ? fromSharedCache : Optional.empty();
	}

	public void onRecentTrades(TradePair tradePair, List<Trade> trades) {
		if (tradePair == null || trades == null || trades.isEmpty()) {
			return;
		}

		String key = pairKey(tradePair);
		int cap = 300;
		List<Trade> copy = new ArrayList<>(trades.size() > cap ? trades.subList(0, cap) : trades);
		recentTradesByPair.put(key, Collections.unmodifiableList(copy));
	}

	public MarketDataSnapshot getLatestSnapshot(TradePair tradePair) {
		if (tradePair == null) {
			return MarketDataSnapshot.empty(null);
		}

		String key = pairKey(tradePair);
		Optional<Ticker> ticker = getTickerFromCache(tradePair, Duration.ofSeconds(30));
		Optional<OrderBook> orderBook = getOrderBookFromCache(tradePair, Duration.ofSeconds(30));
		List<Trade> recentTrades = recentTradesByPair.getOrDefault(key, List.of());

		Instant updatedAt = Instant.now();
		if (!recentTrades.isEmpty() && recentTrades.getFirst() != null && recentTrades.getFirst().getTimestamp() != null) {
			updatedAt = recentTrades.getFirst().getTimestamp();
		} else if (orderBook.isPresent() && orderBook.get().getTimestamp() != null) {
			updatedAt = orderBook.get().getTimestamp();
		} else if (ticker.isPresent()) {
			updatedAt = Instant.ofEpochMilli(Math.max(1L, ticker.get().getTimestamp()));
		}

		return new MarketDataSnapshot(
				tradePair,
				ticker.orElse(null),
				orderBook.orElse(null),
				recentTrades,
				updatedAt);
	}

	private static boolean isFresh(Instant updatedAt, Duration maxAge) {
		if (updatedAt == null) {
			return false;
		}
		if (maxAge == null || maxAge.isZero() || maxAge.isNegative()) {
			return true;
		}
		return updatedAt.isAfter(Instant.now().minus(maxAge));
	}

	private static String pairKey(TradePair tradePair) {
		return tradePair.toCompactSymbol();
	}

	private static MarketQuote toMarketQuote(TradePair tradePair, Ticker ticker) {
		return MarketQuote.builder()
				.tradePair(tradePair)
				.bid(Math.max(0.0, ticker.getBidPrice()))
				.ask(Math.max(0.0, ticker.getAskPrice()))
				.last(Math.max(0.0, ticker.getLastPrice()))
				.volume(Math.max(0.0, ticker.getVolume()))
				.changePercent(ticker.getChangePercent())
				.high24h(Math.max(0.0, ticker.getHighPrice()))
				.low24h(Math.max(0.0, ticker.getLowPrice()))
				.updatedAt(Instant.ofEpochMilli(Math.max(1L, ticker.getTimestamp())))
				.source("Coinbase")
				.build();
	}

	private static Ticker toTicker(MarketQuote quote) {
		Ticker ticker = new Ticker();
		ticker.setBidPrice(Math.max(0.0, quote.getBid()));
		ticker.setAskPrice(Math.max(0.0, quote.getAsk()));
		ticker.setLastPrice(Math.max(0.0, quote.getLast()));
		ticker.setVolume(Math.max(0.0, quote.getVolume()));
		ticker.setTimestamp(quote.getUpdatedAt().toEpochMilli());
		ticker.setHighPrice(Math.max(0.0, quote.getHigh24h()));
		ticker.setLowPrice(Math.max(0.0, quote.getLow24h()));
		return ticker;
	}

	private record TickerEnvelope(Ticker ticker, Instant updatedAt) {
	}

	private record OrderBookEnvelope(OrderBook orderBook, Instant updatedAt) {
	}

	public record MarketDataSnapshot(
			TradePair tradePair,
			Ticker ticker,
			OrderBook orderBook,
			List<Trade> recentTrades,
			Instant updatedAt) {
		public static MarketDataSnapshot empty(TradePair tradePair) {
			return new MarketDataSnapshot(tradePair, null, null, List.of(), Instant.now());
		}
	}
}
