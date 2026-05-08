package org.investpro.ui;

import lombok.extern.slf4j.Slf4j;

import javafx.application.Platform;
import org.investpro.data.Account;

import org.investpro.data.CandleData;
import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.Position;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Bridges exchange streaming events into the JavaFX TradingWindow.
 * <p>
 * This class should stay UI-focused:
 * - receive exchange stream callbacks
 * - switch safely to JavaFX thread
 * - update TradingWindow UI state
 * <p>
 * SystemCore can own the main streaming lifecycle.
 */
@Slf4j
public class DesktopExchangeStreamBridge implements ExchangeStreamConsumer {
    private final TradingWindow window;

    public DesktopExchangeStreamBridge(TradingWindow window) {
        this.window = Objects.requireNonNull(window, "window cannot be null");
    }

    @Override
    public void onConnected(String exchangeName) {
        runOnUiThread(() -> {
            window.updateStreamingStatus("Streaming: %s".formatted(safe(exchangeName)));
            window.appendJournal("Stream connected: %s".formatted(safe(exchangeName)));
        });
    }

    @Override
    public void onDisconnected(String exchangeName, String reason) {
        runOnUiThread(() -> {
            window.updateStreamingStatus("Stream disconnected");
            window.appendJournal(
                    "Stream disconnected: %s | Reason: %s"
                            .formatted(safe(exchangeName), safe(reason))
            );
        });
    }

    @Override
    public void onError(String exchangeName, Throwable throwable) {
        String message = rootMessage(throwable);

        log.warn("Stream error from {}: {}", exchangeName, message, throwable);

        runOnUiThread(() -> {
            window.updateStreamingStatus("Stream error");
            window.appendJournal(
                    "Stream error from %s: %s"
                            .formatted(safe(exchangeName), message)
            );
        });
    }

    @Override
    public void onTicker(String exchangeName, TradePair tradePair, Ticker ticker) {
        if (tradePair == null || ticker == null) {
            log.error(
                    "Trading pair is null or Ticker is null"
            );
            return;
        }

        runOnUiThread(() -> window.updateTickerFromStream(tradePair, ticker));
    }

    @Override
    public void onTrade(String exchangeName, TradePair tradePair, Trade trade) {
        if (tradePair == null || trade == null) {
            log.error("trade pair is null or Trade is null");
            return;
        }

        runOnUiThread(() -> window.updateTradeFromStream(trade));
    }

    @Override
    public void onOrderBook(String exchangeName, TradePair tradePair, OrderBook orderBook) {
        if (tradePair == null || orderBook == null) {
            log.error(
                    "Trading pair is null or OrderBook is null"
            );
            return;
        }

        runOnUiThread(() -> window.updateOrderBookFromStream(tradePair, orderBook));
    }

    @Override
    public void onCandle(String exchangeName, TradePair tradePair, CandleData candleData) {
        if (tradePair == null || candleData == null) {
            return;
        }

        runOnUiThread(() -> window.updateCandleFromStream(tradePair, candleData));
    }

    @Override
    public void onAccount(String exchangeName, Account account) {
        if (account == null) {
            return;
        }

        runOnUiThread(() -> {
            window.updateAccountFromStream(account);
            window.appendJournal("Account stream update from %s".formatted(safe(exchangeName)));
        });
    }

    @Override
    public void onBalanceChanged(String exchangeName, Account account) {
        if (account == null) {
            return;
        }

        runOnUiThread(() -> {
            window.updateAccountFromStream(account);
            window.appendJournal("Balance stream update from %s".formatted(safe(exchangeName)));
        });
    }

    @Override
    public void onOpenOrder(String exchangeName, OpenOrder order) {
        if (order == null) {
            return;
        }

        runOnUiThread(() -> window.updateOpenOrderFromStream(order));
    }

    @Override
    public void onPosition(String exchangeName, Position position) {
        if (position == null) {
            return;
        }

        runOnUiThread(() -> window.updatePositionFromStream(position));
    }

    @Override
    public void onOrderAccepted(String exchangeName, String orderId) {
        runOnUiThread(() -> {
            window.appendJournal(
                    "Order accepted on %s: %s"
                            .formatted(safe(exchangeName), safe(orderId))
            );
            window.refreshAccountWorkspace();
        });
    }

    @Override
    public void onOrderRejected(String exchangeName, String clientOrderId, String reason) {
        runOnUiThread(() -> {
            window.appendJournal(
                    "Order rejected on %s: clientOrderId=%s reason=%s"
                            .formatted(safe(exchangeName), safe(clientOrderId), safe(reason))
            );
            window.refreshAccountWorkspace();
        });
    }

    @Override
    public void onOrderFilled(String exchangeName, String orderId, Trade fill) {
        runOnUiThread(() -> {
            window.appendJournal(
                    "Order filled on %s: orderId=%s"
                            .formatted(safe(exchangeName), safe(orderId))
            );

            if (fill != null && fill.getTradePair() != null) {
                window.updateTradeFromStream(fill);
            }

            window.refreshAccountWorkspace();
        });
    }

    @Override
    public void onOrderCancelled(String exchangeName, String orderId) {
        runOnUiThread(() -> {
            window.appendJournal(
                    "Order cancelled on %s: orderId=%s"
                            .formatted(safe(exchangeName), safe(orderId))
            );
            window.refreshAccountWorkspace();
        });
    }

    @Override
    public void onRawMessage(String exchangeName, String channel, String rawJson) {
        log.debug(
                "Raw stream message exchange={} channel={} payload={}",
                exchangeName,
                channel,
                rawJson
        );
    }

    private void runOnUiThread(Runnable runnable) {
        if (runnable == null) {
            return;
        }

        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }

    @Contract(pure = true)
    private static @NotNull String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static @NotNull String rootMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown stream error";
        }

        Throwable current = throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();

        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }
}