package org.investpro.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SymbolNormalizerTest {

    @Test
    void normalizeUnderscore() {
        assertEquals("EUR/USD", SymbolNormalizer.normalize("EUR_USD"));
    }

    @Test
    void normalizeDash() {
        assertEquals("EUR/USD", SymbolNormalizer.normalize("EUR-USD"));
    }

    @Test
    void normalizeSlash() {
        assertEquals("EUR/USD", SymbolNormalizer.normalize("EUR/USD"));
    }

    @Test
    void toUnderscore() {
        assertEquals("EUR_USD", SymbolNormalizer.toUnderscore("EUR/USD"));
    }

    @Test
    void toDash() {
        assertEquals("EUR-USD", SymbolNormalizer.toDash("EUR/USD"));
    }

    @Test
    void toCompact() {
        assertEquals("EURUSD", SymbolNormalizer.toCompact("EUR/USD"));
    }

    @Test
    void forExchangeOanda() {
        assertEquals("EUR_USD", SymbolNormalizer.forExchange("EUR/USD", "oanda"));
    }

    @Test
    void forExchangeCoinbase() {
        assertEquals("EUR-USD", SymbolNormalizer.forExchange("EUR/USD", "coinbase"));
    }

    @Test
    void forExchangeBinanceUs() {
        assertEquals("EURUSD", SymbolNormalizer.forExchange("EUR/USD", "binance-us"));
    }

    @Test
    void normalizeCompactBtcUsdt() {
        assertEquals("BTC/USDT", SymbolNormalizer.normalize("BTCUSDT"));
    }

    @Test
    void normalizeBtcDashUsd() {
        assertEquals("BTC/USD", SymbolNormalizer.normalize("BTC-USD"));
    }
}
