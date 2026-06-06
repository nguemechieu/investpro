package org.investpro.terminal.adapter;

import org.investpro.data.CandleData;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.TradePair;
import org.investpro.terminal.domain.Candle;
import org.investpro.terminal.domain.InstrumentId;
import org.investpro.terminal.domain.MarketTick;
import org.investpro.terminal.domain.OrderBookSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalExchangeMapperTest {

    @Test
    void mapsTickerToTerminalMarketTick() throws Exception {
        TradePair pair = TradePair.of("BTC", "USDC");
        Ticker ticker = new Ticker(101.5, 101.0, 102.0, 3.25, 1_717_000_000_000L);

        MarketTick tick = TerminalExchangeMapper.marketTick("coinbase", pair, ticker);

        assertEquals(new InstrumentId("coinbase", "BTC/USDC", "BTC/USDC"), tick.instrumentId());
        assertEquals(BigDecimal.valueOf(101.0), tick.bid());
        assertEquals(BigDecimal.valueOf(102.0), tick.ask());
        assertEquals(BigDecimal.valueOf(101.5), tick.last());
        assertEquals(BigDecimal.valueOf(3.25), tick.volume());
    }

    @Test
    void mapsOrderBookLevels() throws Exception {
        TradePair pair = TradePair.of("ETH", "USD");
        OrderBook orderBook = new OrderBook(
                pair,
                List.of(new OrderBook.PriceLevel(2499.5, 1.2, 2)),
                List.of(new OrderBook.PriceLevel(2500.5, 0.8, 1)));

        OrderBookSnapshot snapshot = TerminalExchangeMapper.orderBook("oanda", pair, orderBook);

        assertEquals("ETH/USD", snapshot.instrumentId().symbol());
        assertEquals(BigDecimal.valueOf(2499.5), snapshot.bids().getFirst().price());
        assertEquals(BigDecimal.valueOf(1.2), snapshot.bids().getFirst().quantity());
        assertEquals(2, snapshot.bids().getFirst().orderCount());
        assertEquals(BigDecimal.valueOf(2500.5), snapshot.asks().getFirst().price());
    }

    @Test
    void preservesPlaceholderCandlesForSparseMarkets() throws Exception {
        TradePair pair = TradePair.of("BTCLN", "XLM");
        CandleData placeholder = new CandleData(10.0, 10.5, 11.0, 9.5, 1_717_000_000, 0.0, 10.25, 0.0, true);

        Candle candle = TerminalExchangeMapper.candle("stellar", pair, "H1", placeholder);

        assertEquals("BTCLN/XLM", candle.instrumentId().symbol());
        assertEquals(BigDecimal.valueOf(10.0), candle.open());
        assertEquals(BigDecimal.valueOf(11.0), candle.high());
        assertEquals(BigDecimal.valueOf(9.5), candle.low());
        assertEquals(BigDecimal.valueOf(10.5), candle.close());
        assertTrue(candle.placeholder());
    }

    @Test
    void infersStellarAndForexAssetClasses() {
        assertEquals(
                org.investpro.terminal.domain.AssetClass.CRYPTO_STELLAR,
                TerminalExchangeMapper.inferAssetClass("BTCLN", "XLM", "stellar"));
        assertEquals(
                org.investpro.terminal.domain.AssetClass.FOREX,
                TerminalExchangeMapper.inferAssetClass("EUR", "USD", "oanda"));
        assertFalse(TerminalExchangeMapper.inferAssetClass("AAPL", "USD", "alpaca")
                == org.investpro.terminal.domain.AssetClass.UNKNOWN);
    }
}
