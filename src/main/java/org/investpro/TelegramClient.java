package org.investpro;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * TelegramClient for interacting with Telegram Bot API.
 */
@Getter
@Setter
public class TelegramClient {
    private static final String TELEGRAM_API_URL = "https://api.telegram.org/bot";
    private static final Logger logger = Logger.getLogger(TelegramClient.class.getName());

    private final HttpClient client = HttpClient.newHttpClient();
    private final String botToken;

    private String username;
    private String lastMessage;
    private boolean isOnline;

    /**
     * Constructor initializes the Telegram client with a bot token.
     *
     * @param telegramBotToken The bot token obtained from BotFather
     */
    public TelegramClient(String telegramBotToken) {
        this.botToken = telegramBotToken;
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
    public void sendMessage(String chatId, String message) {
        try {
            String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);
            String url = buildApiUrl("sendMessage", "chat_id=" + chatId, "text=" + encodedMessage);

            HttpResponse<String> response = sendRequest(url);

            if (response.statusCode() == 200) {
                logger.info("✅ Message sent successfully to chat ID: " + chatId);
            } else {
                logger.warning("❌ Failed to send message. Response Code: " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            handleException("Error sending message to Telegram", e);
        }
    }

    /**
     * Checks if the bot is online by calling the getMe API.
     *
     * @return true if bot is online, false otherwise
     */
    public boolean checkBotStatus() {
        try {
            String url = buildApiUrl("getMe");
            HttpResponse<String> response = sendRequest(url);
            return response.statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            handleException("Error checking bot status", e);
            return false;
        }
    }

    /**
     * Retrieves the bot's username.
     *
     * @return the bot's username or null if an error occurs
     */
    private String fetchBotUsername() {
        try {
            String url = buildApiUrl("getMe");
            HttpResponse<String> response = sendRequest(url);

            JSONObject jsonResponse = new JSONObject(response.body());
            return jsonResponse.optJSONObject("result").optString("username", null);
        } catch (IOException | InterruptedException e) {
            handleException("Error fetching bot username", e);
            return null;
        }
    }

    /**
     * Fetches the last received message.
     *
     * @return the last received message text or null if no messages exist
     */
    public String getLastMessage() {
        try {
            String url = buildApiUrl("getUpdates");
            HttpResponse<String> response = sendRequest(url);

            JSONArray updates = new JSONObject(response.body()).optJSONArray("result");
            if (updates != null && !updates.isEmpty()) {
                JSONObject lastUpdate = updates.getJSONObject(updates.length() - 1);
                lastMessage = lastUpdate.optJSONObject("message").optString("text", null);
                return lastMessage;
            }
        } catch (IOException | InterruptedException e) {
            handleException("Error fetching last message", e);
        }
        return null;
    }

    /**
     * Retrieves the latest chat ID that sent a message to the bot.
     *
     * @return The chat ID or null if no messages exist
     */
    public String getChatId() {
        try {
            String url = buildApiUrl("getUpdates");
            HttpResponse<String> response = sendRequest(url);

            JSONArray updates = new JSONObject(response.body()).optJSONArray("result");

            if (updates != null && !updates.isEmpty()) {
                JSONObject lastUpdate = updates.getJSONObject(updates.length() - 1);
                return lastUpdate.optJSONObject("message").optJSONObject("chat").optString("id", null);
            } else {
                logger.warning("⚠ No messages found. Cannot determine chat ID.");
                return null;
            }
        } catch (IOException | InterruptedException e) {
            handleException("Error fetching chat ID", e);
            return null;
        }
    }

    /**
     * Runs the Telegram bot (Placeholder for future enhancements).
     */
    public void run() {
        logger.info("🤖 Telegram Bot is running...");
    }

    /**
     * Constructs an API URL with the given endpoint and parameters.
     *
     * @param endpoint   The Telegram API method (e.g., "sendMessage")
     * @param parameters Optional query parameters
     * @return The full API URL as a String
     */
    private String buildApiUrl(String endpoint, String... parameters) {
        StringBuilder url = new StringBuilder(TELEGRAM_API_URL).append(botToken).append("/").append(endpoint);
        if (parameters.length > 0) {
            url.append("?").append(String.join("&", parameters));
        }
        return url.toString();
    }

    /**
     * Sends an HTTP request to the given URL.
     *
     * @param url The API URL to send the request to.
     * @return HttpResponse object containing the server response.
     * @throws IOException          If an I/O error occurs.
     * @throws InterruptedException If the request is interrupted.
     */
    private HttpResponse<String> sendRequest(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Handles exceptions by logging errors and ensuring thread safety.
     *
     * @param message The error message to log.
     * @param e       The exception that occurred.
     */
    private void handleException(String message, @NotNull Exception e) {
        logger.severe("❌ " + message + ": " + e.getMessage());
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
