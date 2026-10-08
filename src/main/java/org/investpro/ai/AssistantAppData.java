package org.investpro.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Explicit, bounded data projections; never serialize exchanges, credentials or user profiles. */
public final class AssistantAppData {
    private static final ObjectMapper JSON = new ObjectMapper();
    private AssistantAppData() { }

    public static String venueId(String name) {
        return Objects.toString(name, "").trim().toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
    }

    public static String execute(String command, Map<String, Exchange> venues) {
        String[] args = command.trim().split("\\s+");
        ObjectNode result = JSON.createObjectNode().put("snapshotTime", Instant.now().toString());
        try {
            if (args[0].equalsIgnoreCase("/venues")) {
                var list = result.putArray("venues");
                venues.forEach((id, exchange) -> list.addObject().put("venue", id)
                        .put("name", exchange.getDisplayName()).put("mode", exchange.getResolvedTradingMode())
                        .put("connected", exchange.isAuthenticatedSessionConnected() || Boolean.TRUE.equals(exchange.isConnected())));
                return result.toString();
            }
            if (args.length < 3) return "Usage: /data VENUE account|positions|orders|symbols|quote|candles|orderbook [SYMBOL] [SECONDS] [LIMIT]";
            Exchange exchange = venues.get(venueId(args[1]));
            if (exchange == null) return "Unknown or unavailable venue. Use /venues for current venue IDs.";
            result.put("venue", args[1]).put("mode", exchange.getResolvedTradingMode());
            String action = args[2].toLowerCase(Locale.ROOT);
            switch (action) {
                case "account" -> {
                    var account = exchange.tradingAccount().get(10, TimeUnit.SECONDS);
                    if (account == null) throw new IllegalStateException("Account unavailable");
                    result.put("currency", account.getBaseCurrency()).put("totalBalance", account.getTotalBalance())
                            .put("equity", account.getEquity()).put("marginUsed", account.getMarginUsed())
                            .put("marginAvailable", account.getMarginAvailable());
                    result.set("balances", JSON.valueToTree(account.getBalances()));
                    result.set("availableBalances", JSON.valueToTree(account.getAvailableBalances()));
                }
                case "positions" -> {
                    var positions = exchange.isPaperTrading() ? exchange.localPaperPositions()
                            : exchange.fetchAllPositions().get(10, TimeUnit.SECONDS);
                    var list = result.putArray("positions");
                    positions.stream().filter(Objects::nonNull).limit(100).forEach(position -> list.addObject()
                            .put("symbol", position.getSymbol()).put("side", Objects.toString(position.getSide(), ""))
                            .put("quantity", position.getQuantity()).put("entryPrice", position.getEntryPrice())
                            .put("currentPrice", position.getCurrentPrice()).put("unrealizedPnl", position.getUnrealizedPnl())
                            .put("realizedPnl", position.getRealizedPnl()).put("leverage", position.getLeverage())
                            .put("stopLoss", position.getStopLoss()).put("takeProfit", position.getTakeProfit()));
                    result.put("total", positions.size());
                }
                case "orders" -> {
                    var orders = exchange.orderExecution().fetchAllOpenOrders().get(10, TimeUnit.SECONDS);
                    var list = result.putArray("orders");
                    orders.stream().filter(Objects::nonNull).limit(100).forEach(order -> list.addObject()
                            .put("id", order.getOrderId()).put("symbol", symbol(order.getTradePair()))
                            .put("side", Objects.toString(order.getSide(), "")).put("type", Objects.toString(order.getOrderType(), ""))
                            .put("quantity", order.getSize()).put("filled", order.getFilledSize())
                            .put("price", order.getPrice()).put("status", Objects.toString(order.getStatus(), "")));
                    result.put("total", orders.size());
                }
                case "symbols" -> {
                    String filter = args.length > 3 ? args[3].toUpperCase(Locale.ROOT) : "";
                    var list = result.putArray("symbols");
                    var pairs = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        try { return exchange.getTradePairSymbol(); }
                        catch (Exception error) { throw new java.util.concurrent.CompletionException(error); }
                    }).get(10, TimeUnit.SECONDS);
                    var matches = pairs.stream().filter(Objects::nonNull)
                            .filter(pair -> symbol(pair).toUpperCase(Locale.ROOT).contains(filter) || pair.toSlashSymbol().contains(filter)).toList();
                    matches.stream().limit(100).forEach(pair -> list.addObject().put("symbol", symbol(pair)).put("pair", pair.toSlashSymbol()));
                    result.put("totalMatches", matches.size());
                }
                case "quote", "candles", "orderbook" -> {
                    if (args.length < 4) return "Specify an exchange-native symbol or a pair such as BTC/USD.";
                    TradePair pair = TradePair.fromSymbol(args[3]);
                    result.put("symbol", symbol(pair));
                    if (action.equals("quote")) {
                        var quote = exchange.fetchTicker(pair).get(10, TimeUnit.SECONDS);
                        result.put("bid", quote.getBidPrice()).put("ask", quote.getAskPrice()).put("last", quote.getLastPrice())
                                .put("volume", quote.getVolume()).put("quoteTimestampMillis", quote.getTimestamp())
                                .put("quoteType", Objects.toString(quote.getQuoteType(), "UNKNOWN"));
                    } else if (action.equals("orderbook")) {
                        var book = exchange.fetchOrderBook(pair).get(10, TimeUnit.SECONDS);
                        var bids = result.putArray("bids"); var asks = result.putArray("asks");
                        book.getBids().stream().limit(10).forEach(level -> bids.addObject().put("price", level.getPrice()).put("size", level.getSize()));
                        book.getAsks().stream().limit(10).forEach(level -> asks.addObject().put("price", level.getPrice()).put("size", level.getSize()));
                        result.put("bookTimestamp", Objects.toString(book.getTimestamp(), "unknown"));
                    } else {
                        int seconds = args.length > 4 ? Integer.parseInt(args[4]) : 3600;
                        int limit = args.length > 5 ? Integer.parseInt(args[5]) : 60;
                        if (limit < 1 || limit > 100 || seconds <= 0) return "Use 1–100 candles and a positive timeframe in seconds.";
                        var supplier = exchange.getCandleDataSupplier(seconds, pair);
                        if (supplier == null || !supplier.getSupportedGranularities().contains(seconds))
                            return "This venue does not support the requested candle timeframe.";
                        var candles = supplier.get().get(10, TimeUnit.SECONDS).stream()
                                .filter(candle -> !candle.placeHolder()).sorted(Comparator.comparingInt(org.investpro.data.CandleData::openTime)).toList();
                        var list = result.putArray("candles");
                        candles.subList(Math.max(0, candles.size() - limit), candles.size()).forEach(candle -> list.addObject()
                                .put("openTime", candle.openTime()).put("open", candle.openPrice()).put("high", candle.highPrice())
                                .put("low", candle.lowPrice()).put("close", candle.closePrice()).put("volume", candle.volume()));
                        result.put("secondsPerCandle", seconds).put("timestampUnit", "epoch seconds")
                                .put("includesFormingBar", candles.stream().anyMatch(candle -> (long) candle.openTime() + seconds > Instant.now().getEpochSecond()));
                    }
                }
                default -> { return "Unsupported dataset. Use account, positions, orders, symbols, quote, candles or orderbook."; }
            }
            result.put("source", "InvestPro exchange adapter; do not assume exchange data is current without checking its timestamps.");
            return result.toString();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return "Data request interrupted.";
        } catch (Exception error) {
            Throwable cause = error;
            while (cause.getCause() != null) cause = cause.getCause();
            result.put("available", false).put("errorType", cause.getClass().getSimpleName());
            // Arbitrary adapter messages may contain private request details; only vetted guidance is exposed.
            result.put("message", cause instanceof org.investpro.exchange.ibkr.IbkrMarketDataException
                    ? cause.getMessage() : "Data unavailable. Check connection, contract resolution and data permissions in the desktop app.");
            return result.toString();
        }
    }

    private static String symbol(TradePair pair) {
        if (pair == null) return "";
        return pair.getNativeSymbol() != null && !pair.getNativeSymbol().isBlank()
                ? pair.getNativeSymbol() : pair.toSlashSymbol();
    }
}
