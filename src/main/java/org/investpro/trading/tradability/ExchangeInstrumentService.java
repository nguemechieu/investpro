package org.investpro.trading.tradability;

import org.investpro.models.trading.TradePair;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ExchangeInstrumentService {

    /** Force-refresh the list of tradeable pairs from the exchange API. */
    CompletableFuture<List<TradePair>> refreshTradeablePairs();

    /** Return cached tradeable pairs, fetching if the cache is empty. */
    CompletableFuture<List<TradePair>> getTradeablePairs();

    /** Return true if the given pair is in the exchange's tradeable list. */
    CompletableFuture<Boolean> isPairSupported(TradePair pair);

    /** Return the high-level trade status for a single pair. */
    CompletableFuture<InstrumentTradeStatus> getTradeStatus(TradePair pair);

    /** Return a detailed tradeability report explaining why a pair is or isn't tradeable. */
    CompletableFuture<InstrumentTradeabilityReport> explainTradeability(TradePair pair);

    /** The exchange identifier this service targets (e.g. "oanda", "coinbase", "binance-us"). */
    String getExchangeId();

    /** Clear the cached pair list so the next call to {@link #getTradeablePairs()} re-fetches. */
    void invalidateCache();
}
