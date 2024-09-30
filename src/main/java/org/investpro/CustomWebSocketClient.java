package org.investpro;



import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.investpro.Exchange.logger;


public class CustomWebSocketClient {

    private WebSocket webSocket;

    // Function to establish a WebSocket connection based on the given URL and send a message
    public CompletableFuture<String> sendWebSocketRequest(String url, String message) {
        CompletableFuture<String> responseFuture = new CompletableFuture<>();

        // Create an HTTP client
        HttpClient client = HttpClient.newHttpClient();

        // Create and connect the WebSocket
        webSocket = client.newWebSocketBuilder()
                .buildAsync(URI.create(url), new WebSocket.Listener() {

                    // Method called when the WebSocket is opened
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        logger.debug(
                                "Connected to WebSocket server at URL: {}", url);

                        webSocket.sendText(message, true);  // Send the request message
                        WebSocket.Listener.super.onOpen(webSocket);
                    }


                    // Method called when a text message is received from the server
                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                      logger.info(
                               "Received text data: {}", data.toString()

                      );
                        responseFuture.complete(data.toString());  // Complete the future with the response data
                        return WebSocket.Listener.super.onText(webSocket, data, last);
                    }

                    // Method called when a binary message is received from the server (e.g., for non-text data)
                    @Override
                    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {

                         logger.debug(
                                 "Received binary data: {}", data.toString()
                         );
                        return WebSocket.Listener.super.onBinary(webSocket, data, last);
                    }

                    // Method called if the connection is closed
                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {

                        logger.info(
                                "WebSocket closed: " + reason

                        );
                        responseFuture.completeExceptionally(new RuntimeException("WebSocket closed unexpectedly."));
                        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
                    }

                    // Method called if an error occurs
                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {

                         logger.error(
                                 "WebSocket error: " + error.getMessage(), error
                         );
                        responseFuture.completeExceptionally(error);  // Complete the future with an exception
                    }
                }).join();  // Join to wait for connection completion

        return responseFuture;  // Return the future which will be completed when a response is received
    }

    // Method to close the WebSocket connection
    public void closeWebSocket() {
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Closing WebSocket").thenRun(() ->

                    logger.info("WebSocket connection closed."));
        }
    }

    // Main method for testing the WebSocket client
//    public static void main(String[] args) {
//        CustomWebSocketClient client = new CustomWebSocketClient();
//
//
//        // Example WebSocket URL (replace with the appropriate WebSocket server URL)
//        String url = "wss://stream.binance.us:9443/ws";
//
//        // Example message to send (adjust according to your use case)
//        String message = "{\"method\": \"SUBSCRIBE\", \"params\": [\"ethusdt@kline_1d\"], \"id\": 1}";
//
//        // Send a WebSocket request and listen for the response
//        client.sendWebSocketRequest(url, message).thenAccept(response -> System.out.println("Final response received: " + response)).exceptionally(ex -> {
//            logger.error(
//                    "Error: %s".formatted(ex.getMessage()), ex
//            );
//            return null;
//        });
//
//        // To close the WebSocket connection after some time (optional)
//        // Example: close after 10 seconds (can be adjusted as per requirement)
//        try {
//            Thread.sleep(10000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        client.closeWebSocket();
//    }
}
