package org.investpro;


// A Java program for a Server

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    private static final Logger logger = LoggerFactory.getLogger(Server.class);

    private int port;
    ServerSocket server = new ServerSocket(port);
    Socket socket = server.accept();

    // constructor with port
    public Server(int port) throws IOException {
        // starts server and waits for a connection
        try {

            logger.info("Server started");

            logger.info("Waiting for a client...");

            //initialize socket and input stream

            server.setSoTimeout(5000);
            server.setOption(java.net.StandardSocketOptions.SO_REUSEADDR, true);
            server.setPerformancePreferences(100, 50, 100);
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(InetAddress.getLocalHost(), port));


            logger.info("Client connected");

            // takes input from the client socket
            DataInputStream in = new DataInputStream(
                    new BufferedInputStream(socket.getInputStream()));

            String line = "";

            // reads message from client until "Over" is sent
            while (!line.equals("Over")) {
                try {
                    line = in.readUTF();
                    System.out.println(line);

                } catch (IOException i) {
                    logger.info(
                            "Error reading from client: " + i
                    );
                    throw new RuntimeException(i);
                }
            }

            logger.info("Server closed");
            // close connection
            socket.close();
            in.close();
        } catch (IOException i) {
            logger.info("Error starting server: " + i);
        }
    }


    public void start() {
        new Thread(Server.this::start).start();
    }
}
