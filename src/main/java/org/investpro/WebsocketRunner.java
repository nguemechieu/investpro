package org.investpro;

import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.WebSocketContainer;
import java.io.IOException;
import java.net.URI;

public class WebsocketRunner {
    public static void main(String[] args) {

        WebSocketContainer webSocketContainer = ContainerProvider.getWebSocketContainer();
        webSocketContainer.setDefaultMaxTextMessageBufferSize(1024 * 1024);
        webSocketContainer.setAsyncSendTimeout(10000);
        try {
            webSocketContainer.connectToServer(new Object(), URI.create(
                    "ws://api.binance.us/api/v3/ticker/price?symbol=BTCUSDT"
            ));
        } catch (DeploymentException | IOException e) {
            throw new RuntimeException(e);
        }

        try {
            Thread.sleep(10000);

            WebSocketEndpoint endpoint = (WebSocketEndpoint) webSocketContainer.connectToServer(new Object(), URI.create(
                    "ws://api.binance.us/api/v3/ticker/price?symbol=BTCUSDT"
            ));
            System.out.println(endpoint.getPrices());


        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (DeploymentException | IOException e) {
            throw new RuntimeException(e);
        }
    }

}
