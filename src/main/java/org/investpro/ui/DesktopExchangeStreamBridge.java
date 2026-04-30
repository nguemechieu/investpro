package org.investpro.ui;

import javafx.application.Platform;
import org.investpro.data.Account;
import org.investpro.exchange.ExchangeStreamConsumer;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.Position;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.TradePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DesktopExchangeStreamBridge implements ExchangeStreamConsumer {
    private static final Logger logger = LoggerFactory.getLogger(DesktopExchangeStreamBridge.class);

    private final TradingWindow window;

    public DesktopExchangeStreamBridge(TradingWindow window) {
        this.window = window;
    }

    @Override
    public void onConnected(String exchangeName) {
        runOnUiThread(() -> {
            window.updateStreamingStatus("Streaming: " + exchangeName);
            window.appendJournal("Stream connected: " + exchangeName);
        });
    }

    @Override
    public void onDisconnected(String exchangeName, String reason) {
        runOnUiThread(() -> {
            window.updateStreamingStatus("Stream disconnected");
            window.appendJournal("Stream disconnected: " + exchangeName + " " + reason);
        });
    }

    @Override
    public void onError(String exchangeName, Throwable throwable) {
        logger.warn("Stream error from {}", exchangeName, throwable);
        runOnUiThread(() -> window.appendJournal("Stream error from " + exchangeName + ": " + throwable.getMessage()));
    }

    @Override
    public void onTicker(String exchangeName, TradePair tradePair, Ticker ticker) {
        runOnUiThread(() -> window.updateTickerFromStream(tradePair, ticker));
    }

    @Override
    public void onAccount(String exchangeName, Account account) {
        runOnUiThread(() -> window.appendJournal("Account stream update from " + exchangeName));
    }

    @Override
    public void onOpenOrders(String exchangeName, List<OpenOrder> orders) {
        runOnUiThread(() -> window.updateOpenOrdersFromStream(orders));
    }

    @Override
    public void onPositions(String exchangeName, List<Position> positions) {
        runOnUiThread(() -> window.updatePositionsFromStream(positions));
    }

    private void runOnUiThread(Runnable runnable) {
        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }
}
