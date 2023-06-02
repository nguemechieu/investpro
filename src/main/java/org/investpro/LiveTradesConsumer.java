package org.investpro;

import java.io.IOException;
import java.net.http.WebSocket;
import java.text.ParseException;
import java.util.List;


public interface LiveTradesConsumer {
    void acceptTrades(List<Trade> trades);

    void onConnectionEstablished() throws IOException, InterruptedException, ParseException;

    void onConnectionFailed() throws IOException, InterruptedException;

    void onMessage(String message) throws IOException, InterruptedException;


    void accept(Trade trade);

    void close();
}
