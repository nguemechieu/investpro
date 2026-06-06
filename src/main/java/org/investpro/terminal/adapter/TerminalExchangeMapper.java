package org.investpro.terminal.adapter;

import org.investpro.data.CandleData;
import org.investpro.models.Account;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.TradePair;
import org.investpro.terminal.domain.Asset;
import org.investpro.terminal.domain.AssetClass;
import org.investpro.terminal.domain.Balance;
import org.investpro.terminal.domain.Candle;
import org.investpro.terminal.domain.InstrumentId;
import org.investpro.terminal.domain.MarketTick;
import org.investpro.terminal.domain.OrderBookLevel;
import org.investpro.terminal.domain.OrderBookSnapshot;
import org.investpro.terminal.domain.OrderState;
import org.investpro.terminal.domain.Position;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TerminalExchangeMapper {

    private TerminalExchangeMapper() {
    }

    public static InstrumentId instrumentId(String providerId, TradePair pair) {
        String symbol = pair == null ? "" : pair.toSlashSymbol();
        String nativeSymbol = pair == null || pair.getNativeSymbol() == null || pair.getNativeSymbol().isBlank()
                ? symbol
                : pair.getNativeSymbol();
        return new InstrumentId(providerId, symbol, nativeSymbol);
    }

    public static Asset asset(String code, AssetClass assetClass) {
        return new Asset(code, code, assetClass, "", "");
    }

    public static MarketTick marketTick(String providerId, TradePair pair, Ticker ticker) {
        InstrumentId id = instrumentId(providerId, pair);
        if (ticker == null) {
            return new MarketTick(id, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, Instant.now());
        }
        return new MarketTick(
                id,
                positive(ticker.getBidPrice()),
                positive(ticker.getAskPrice()),
                positive(ticker.hasLastPrice() ? ticker.getLastPrice() : ticker.getMidPrice()),
                positive(ticker.getVolume()),
                ticker.getInstant());
    }

    public static OrderBookSnapshot orderBook(String providerId, TradePair pair, OrderBook orderBook) {
        InstrumentId id = instrumentId(providerId, pair);
        if (orderBook == null) {
            return new OrderBookSnapshot(id, List.of(), List.of(), Instant.now(), "");
        }
        return new OrderBookSnapshot(
                id,
                levels(orderBook.getBids()),
                levels(orderBook.getAsks()),
                orderBook.getTimestamp(),
                orderBook.getSequence());
    }

    public static Candle candle(String providerId, TradePair pair, String timeframe, CandleData candleData) {
        return new Candle(
                instrumentId(providerId, pair),
                timeframe,
                candleData == null ? Instant.EPOCH : candleData.timestamp(),
                candleData == null ? BigDecimal.ZERO : price(candleData.openPrice()),
                candleData == null ? BigDecimal.ZERO : price(candleData.highPrice()),
                candleData == null ? BigDecimal.ZERO : price(candleData.lowPrice()),
                candleData == null ? BigDecimal.ZERO : price(candleData.closePrice()),
                candleData == null ? BigDecimal.ZERO : positive(candleData.volume()),
                candleData != null && candleData.placeHolder());
    }

    public static Balance balance(String accountId, String code, double total, double available, double locked) {
        Asset asset = asset(code, inferAssetClass(code, "", ""));
        return new Balance(accountId, asset, positive(available), positive(total), positive(locked));
    }

    public static Position position(String providerId, org.investpro.models.trading.Position position) {
        TradePair pair = position == null ? null : position.getTradePair();
        String positionId = position == null ? "" : position.getPositionId();
        return new Position(
                positionId,
                instrumentId(providerId, pair),
                position == null ? BigDecimal.ZERO : signedQuantity(position),
                position == null ? BigDecimal.ZERO : positive(position.getEntryPrice()),
                position == null ? BigDecimal.ZERO : BigDecimal.valueOf(position.getUnrealizedPnl()),
                position == null ? Instant.now() : position.getTimestamp());
    }

    public static OrderState orderState(OpenOrder order) {
        if (order == null || order.getStatus() == null) {
            return OrderState.UNKNOWN;
        }
        return switch (order.getStatus()) {
            case PENDING -> OrderState.SUBMITTED;
            case OPEN -> OrderState.ACCEPTED;
            case PARTIALLY_FILLED -> OrderState.PARTIALLY_FILLED;
            case FILLED -> OrderState.FILLED;
            case CANCELLED -> OrderState.CANCELED;
            case REJECTED -> OrderState.REJECTED;
            case EXPIRED -> OrderState.EXPIRED;
            case UNKNOWN -> OrderState.UNKNOWN;
        };
    }

    public static String accountId(Account account, String requestedAccountId, String providerId) {
        if (requestedAccountId != null && !requestedAccountId.isBlank()) {
            return requestedAccountId.trim();
        }
        if (account != null && account.getAccountId() != null && !account.getAccountId().isBlank()) {
            return account.getAccountId();
        }
        if (account != null && account.getAccount() != null && !account.getAccount().isBlank()) {
            return account.getAccount();
        }
        return providerId;
    }

    public static AssetClass inferAssetClass(TradePair pair, String providerId) {
        if (pair == null) {
            return AssetClass.UNKNOWN;
        }
        return inferAssetClass(pair.getBaseCode(), pair.getCounterCode(), providerId);
    }

    public static AssetClass inferAssetClass(String baseCode, String quoteCode, String providerId) {
        String provider = providerId == null ? "" : providerId.toLowerCase(Locale.ROOT);
        if (provider.contains("stellar")) {
            return AssetClass.CRYPTO_STELLAR;
        }
        String base = baseCode == null ? "" : baseCode.toUpperCase(Locale.ROOT);
        String quote = quoteCode == null ? "" : quoteCode.toUpperCase(Locale.ROOT);
        if (isForexCode(base) && isForexCode(quote)) {
            return AssetClass.FOREX;
        }
        if (isCryptoCode(base) || isCryptoCode(quote)) {
            return AssetClass.CRYPTO;
        }
        if ("USD".equals(quote) || "USDC".equals(quote) || "USDT".equals(quote)) {
            return AssetClass.EQUITY;
        }
        return AssetClass.UNKNOWN;
    }

    public static TradePair tradePairFromInstrument(InstrumentId instrumentId) {
        if (instrumentId == null) {
            throw new IllegalArgumentException("instrumentId is required");
        }
        try {
            return TradePair.fromSymbol(instrumentId.symbol());
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to parse instrument symbol " + instrumentId.symbol(), exception);
        }
    }

    public static BigDecimal positive(double value) {
        return !Double.isFinite(value) || value < 0 ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }

    public static BigDecimal price(double value) {
        return !Double.isFinite(value) ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }

    public static List<OrderBookLevel> levels(List<OrderBook.PriceLevel> levels) {
        if (levels == null || levels.isEmpty()) {
            return List.of();
        }
        return levels.stream()
                .map(level -> new OrderBookLevel(
                        price(level.getPrice()),
                        positive(level.getSize()),
                        level.getNumOrders()))
                .toList();
    }

    public static double doubleValue(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    public static boolean sameOrder(OpenOrder openOrder, String externalOrderId, String clientOrderId) {
        if (openOrder == null) {
            return false;
        }
        return matches(openOrder.getOrderId(), externalOrderId)
                || matches(openOrder.getClientOrderId(), clientOrderId);
    }

    public static Map<String, Object> baseMetadata(TradePair pair, String providerId) {
        return Map.of(
                "providerId", providerId == null ? "" : providerId,
                "nativeSymbol", pair == null || pair.getNativeSymbol() == null ? "" : pair.getNativeSymbol(),
                "baseCode", pair == null ? "" : pair.getBaseCode(),
                "quoteCode", pair == null ? "" : pair.getCounterCode());
    }

    private static BigDecimal signedQuantity(org.investpro.models.trading.Position position) {
        double quantity = position.getQuantity();
        if (position.isSell()) {
            quantity = -quantity;
        }
        return BigDecimal.valueOf(quantity);
    }

    private static boolean matches(String actual, String expected) {
        return actual != null && expected != null && !expected.isBlank() && actual.equals(expected);
    }

    private static boolean isCryptoCode(String code) {
        return switch (code) {
            case "BTC", "ETH", "SOL", "XLM", "XRP", "USDC", "USDT", "DAI", "LTC", "BCH", "DOGE", "ADA", "AVAX" -> true;
            default -> false;
        };
    }

    private static boolean isForexCode(String code) {
        return switch (code) {
            case "USD", "EUR", "GBP", "JPY", "CAD", "AUD", "NZD", "CHF", "CNH", "HKD", "SGD", "MXN", "NOK", "SEK" -> true;
            default -> false;
        };
    }
}
