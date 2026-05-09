package org.investpro.exchange.contracts;


public interface ExchangeCapabilities {

    boolean supportsLiveTrading();

    boolean supportsPaperTradingMode();

    boolean supportsOrderBook();

    boolean supportsPositions();

    boolean supportsAccountTrades();

    boolean supportsStopLossTakeProfit();

    boolean supportsBracketOrders();

    boolean supportsLeverage();

    boolean supportsDerivatives();

    boolean supportsForex();

    boolean supportsStocks();

    boolean supportsCrypto();

    boolean supportsAccountStreaming();

    boolean supportsOrderStreaming();

    boolean supportsFillStreaming();

    boolean supportsPositionStreaming();

    boolean supportsBalanceStreaming();

    boolean supportsTickerStreaming();

    boolean supportsOrderBookStreaming();

    boolean supportsCandleStreaming();

    boolean supportsTradeStreaming();
}