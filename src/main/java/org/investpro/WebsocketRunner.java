package org.investpro;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.WebSocketContainer;
import java.io.IOException;
import java.net.*;
import java.nio.channels.SelectionKey;

import  org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class WebsocketRunner {
    private static final Logger logger = LoggerFactory.getLogger(WebsocketRunner.class);
    public static void main(String[] args) throws IOException {
        ServerSocket server = new ServerSocket(9443);


        try (server) {
//            server.setSoTimeout(10000);
//            server.setReuseAddress(true);
//            server.setPerformancePreferences(0, 1, 1);
//            server.getChannel();
//            System.out.println("Server has started on 127.0.0.1:80.\r\nWaiting for a connection…");
//            Socket client = server.accept();
//            SocketAddress addr =
//                    client.getRemoteSocketAddress();
//            client.getChannel().connect(
//                    addr
//            );


            System.out.println("A client connected.");
            //wss://stream.binance.us:9443
            InetSocketAddress add = new InetSocketAddress(
                    "ws://stream.binance.us", 9443
            );
            int deco = 1;
            WebSocketServer webSocketServer = new WebSocketServer(add, deco) {
                @Override
                public void onOpen(WebSocket conn, ClientHandshake handshake) {
                    logger.info("Connection opened");


                }

                @Override
                public void onClose(WebSocket conn, int code, String reason, boolean remote) {

                    logger.info("Connection closed");
                    logger.info("Code: " + code);
                    logger.info("Reason: " + reason);

                }

                @Override
                public void onMessage(WebSocket conn, String message) {
                    logger.info("Message received: " + message);

                }

                @Override
                protected boolean onConnect(SelectionKey key) {
                    return super.onConnect(key);
                }

                @Override
                public void onError(WebSocket conn, Exception ex) {
                    logger.info("Error: " + ex);

                }

                @Override
                public void onStart() {

                    logger.info("Server started");

                }
            };
            webSocketServer.start();
            logger.info("Server started");
            while (true)  {
                Socket client = server.accept();
                SocketAddress addr =
                        client.getRemoteSocketAddress();
                client.getChannel().connect(
                        addr
                );
                logger.info("A client connected.");
                //wss://stream.binance.us:9443

            }

        }
        catch (IOException e) {
            e.printStackTrace();
        }

    }

}
