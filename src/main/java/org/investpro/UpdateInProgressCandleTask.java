package org.investpro;

import java.io.IOException;
import java.util.List;

public interface UpdateInProgressCandleTask extends LiveTradesConsumer {
    static LiveTradesConsumer wrap(UpdateInProgressCandleTask liveTradesConsumer) {
        return new LiveTradesConsumer() {
            @Override
            public void acceptTrades(List<Trade> trades) {
                liveTradesConsumer.acceptTrades(trades);
            }

            @Override
            public void onConnectionEstablished() throws InterruptedException {
                liveTradesConsumer.onConnectionEstablished();
            }

            @Override
            public void onConnectionFailed() {
                liveTradesConsumer.onConnectionFailed();
            }

            @Override
            public void onMessage(String message) throws IOException, InterruptedException {
                liveTradesConsumer.onMessage(message);
            }

            @Override
            public void accept(Trade trade) {
                liveTradesConsumer.accept(trade);
            }

            @Override
            public void close() {
                liveTradesConsumer.close();
            }
        };
    }

    void acceptTrades(List<Trade> trades);

    void onConnectionEstablished() throws InterruptedException;

    void onConnectionFailed();

    void onMessage(String message) throws IOException, InterruptedException;

    void accept(Trade trade);

    void close();
}
