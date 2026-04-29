package org.investpro.investpro;

import org.investpro.investpro.models.CoinInfo;
import org.investpro.investpro.models.OrderBook;
import org.investpro.investpro.models.TradePair;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface MarketDataProvider {
    CompletableFuture<List<OrderBook>> fetchOrderBook(TradePair tradePair);

    double fetchLivesBidAsk(TradePair tradePair);

    List<CoinInfo> getCoinInfoList();
}
