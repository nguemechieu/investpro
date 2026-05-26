//package org.investpro.marketdata;
//
//import java.time.Instant;
//import java.util.List;
//import java.util.concurrent.CompletableFuture;
//
//public interface MarketDataService {
//    CompletableFuture<MarketSnapshot> snapshot(String exchangeId, String symbol, String timeframe);
//
//    CompletableFuture<List<Candle>> historicalCandles(
//            String exchangeId,
//            String symbol,
//            String timeframe,
//            Instant from,
//            Instant to);
//
//    CompletableFuture<List<MarketTick>> liveTrades(String exchangeId, String symbol);
//
//    MarketDataConnectionStatus connectionStatus(String exchangeId, String symbol);
//}
