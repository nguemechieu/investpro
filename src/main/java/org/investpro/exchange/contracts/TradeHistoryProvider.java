package org.investpro.exchange.contracts;

import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface TradeHistoryProvider {

    CompletableFuture<List<Trade>> fetchAccountTrades(TradePair tradePair);

    CompletableFuture<List<Trade>> fetchAccountTradesSince(TradePair tradePair, Instant since);

    CompletableFuture<List<Trade>> fetchAccountTradesBetween(TradePair tradePair, Instant from, Instant to);
}