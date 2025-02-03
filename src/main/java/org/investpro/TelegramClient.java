package org.investpro;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import lombok.Getter;
import lombok.Setter;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * TelegramClient for interacting with Telegram Bot API.
 */
@Getter
@Setter
public class TelegramClient {
    private static final String TELEGRAM_API_URL = "https://api.telegram.org/bot";
    private static final Logger logger = Logger.getLogger(TelegramClient.class.getName());

    private static final HttpClient client = HttpClient.newBuilder().build();
    private static final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
    private static String botToken;

    private String username;
    private String lastMessage;
    private boolean isOnline;

    /**
     * Constructor to initialize the Telegram client with a bot token.
     *
     * @param telegramBotToken The bot token obtained from BotFather
     */
    public TelegramClient(String telegramBotToken) {
        botToken = telegramBotToken;
        this.isOnline = checkBotStatus();
        if (isOnline) {
            this.username = fetchBotUsername();
        }
    }

    /**
     * Sends a message to a specific Telegram chat.
     *
     * @param chatId  The Telegram chat ID or username (e.g., @username)
     * @param message The message to send
     */
    public static void sendMessage(String chatId, String message) {
        try {
            String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);
            String urlString = String.format("%s%s/sendMessage?chat_id=%s&text=%s",
                    TELEGRAM_API_URL, botToken, chatId, encodedMessage);

            HttpRequest request = requestBuilder
                    .uri(URI.create(urlString))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                logger.info("✅ Message sent successfully to chat ID: " + chatId);
            } else {
                logger.warning("❌ Failed to send message. Response Code: " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            logger.severe("❌ Error sending message to Telegram: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Checks if the bot is online by calling the getMe API.
     *
     * @return true if bot is online, false otherwise
     */
    public boolean checkBotStatus() {
        try {
            String urlString = TELEGRAM_API_URL + botToken + "/getMe";

            HttpRequest request = requestBuilder
                    .uri(URI.create(urlString))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            return response.statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            logger.severe("❌ Error checking bot status: " + e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Retrieves the bot's username.
     *
     * @return the bot's username
     */
    private String fetchBotUsername() {
        try {
            String urlString = TELEGRAM_API_URL + botToken + "/getMe";

            HttpRequest request = requestBuilder
                    .uri(URI.create(urlString))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            JSONObject jsonResponse = new JSONObject(response.body());
            return jsonResponse.getJSONObject("result").getString("username");
        } catch (IOException | InterruptedException e) {
            logger.severe("❌ Error fetching bot username: " + e.getMessage());
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Fetches the last received message.
     *
     * @return the last received message text
     */
    public String getLastMessage() {
        try {
            String urlString = TELEGRAM_API_URL + botToken + "/getUpdates";

            HttpRequest request = requestBuilder
                    .uri(URI.create(urlString))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            JSONObject jsonResponse = new JSONObject(response.body());
            JSONArray updates = jsonResponse.getJSONArray("result");

            if (!updates.isEmpty()) {
                JSONObject lastUpdate = updates.getJSONObject(updates.length() - 1);
                lastMessage = lastUpdate.getJSONObject("message").getString("text");
                return lastMessage;
            }
        } catch (IOException | InterruptedException e) {
            logger.severe("❌ Error fetching last message: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
        return null;
    }

    /**
     * Runs the Telegram bot in a loop (for future enhancements).
     */
    public void run() {
        logger.info("🤖 Telegram Bot is running...");
    }

    /**
     * Returns the bot's online status.
     *
     * @return true if bot is online, false otherwise
     */
    public boolean isOnline() {
        return isOnline;
    }

    public String getChatId() {
        try {
            String urlString = TELEGRAM_API_URL + botToken + "/getUpdates";
            HttpResponse<String> res = client.send(requestBuilder.uri(URI.create(urlString)).build(),
                    HttpResponse.BodyHandlers.ofString());


            String response = res.body();


            JSONObject jsonResponse = new JSONObject(response);


            JSONArray updates = jsonResponse.getJSONArray("result");

            if (!updates.isEmpty()) {
                JSONObject lastUpdate = updates.getJSONObject(updates.length() - 1);
                return lastUpdate.getJSONObject("message").getJSONObject("chat").get("id").toString();
            } else {
                logger.warning("⚠ No messages found. Cannot determine chat ID." + res);
                return null;
            }
        } catch (IOException e) {
            logger.severe("❌ Error fetching chat ID: " + e.getMessage());
            return null;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
