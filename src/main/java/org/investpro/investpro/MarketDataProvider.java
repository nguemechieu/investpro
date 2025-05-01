package org.investpro.investpro;

import org.investpro.investpro.model.CoinInfo;
import org.investpro.investpro.model.OrderBook;
import org.investpro.investpro.model.TradePair;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface MarketDataProvider {
    CompletableFuture<List<OrderBook>> fetchOrderBook(TradePair tradePair);

    double fetchLivesBidAsk(TradePair tradePair);

    List<CoinInfo> getCoinInfoList();
}
