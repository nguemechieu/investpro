package org.investpro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

/**
 * Improved Telegram Bot Client with non-blocking polling and enhanced API support.
 */
@Getter
@Setter
public class TelegramClient {
    private static final String TELEGRAM_API_URL = "https://api.telegram.org/bot";
    private static final Logger logger = Logger.getLogger(TelegramClient.class.getName());

    private final HttpClient client = HttpClient.newHttpClient();
    private final String botToken;
    private String username;
    private boolean isOnline;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    // Thread-safe storage for messages
    private List<TelegramBotInfo> updatedData = new ArrayList<>();
    private long chatId;
    private int lastUpdateId = 0;

    /**
     * Initializes the Telegram client and starts polling.
     */
    public TelegramClient(String telegramBotToken) {
        this.botToken = telegramBotToken;
        this.isOnline = checkBotStatus();
        if (isOnline) {
            updatedData=fetchUpdates();
            this.username = updatedData.stream().findFirst().isEmpty() ? "N/A" : updatedData.stream().findFirst().get().getUsername();
            this.chatId =updatedData.stream().findFirst().get().getChatId();

            logger.info("Bot started successfully");
        }

    }



    private boolean checkBotStatus() {
        String urlString = TELEGRAM_API_URL + botToken + "/getMe";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlString))
                .GET()
                .build();

        try {
            HttpResponse<String> responseJson;
            try {
                responseJson = client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            ObjectMapper mapper = new ObjectMapper();
            TelegramBotInfo botInfo = mapper.readValue(responseJson.body(), TelegramBotInfo.class);
            return botInfo.getId() > 0;
        } catch (IOException e) {
            handleException("Error checking bot status", e);
            return false;
        }
    }


    /**
     * Sends a message.
     */
    public void sendMessage(long chatId, String message) {
        try {
            sendChatAction(String.valueOf(chatId), ENUM_CHAT_ACTION.typing);
            String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);
            String url = buildApiUrl("sendMessage", "chat_id=" + chatId, "text=" + encodedMessage, "parse_mode=Markdown");
            HttpResponse<String> response = sendRequest(url);
            logResponse(response, "Message sent to chat ID: " + chatId);
        } catch (IOException | InterruptedException e) {
            handleException("Error sending message", e);
        }
    }

    /**
     * Sends a chat action (e.g., "typing", "upload_photo").
     */
    public void sendChatAction(String chatId, ENUM_CHAT_ACTION action) {
        try {
            String url = buildApiUrl("sendChatAction", "chat_id=" + chatId, "action=" + action);
            HttpResponse<String> response = sendRequest(url);
            logResponse(response, "Chat action '" + action + "' sent to chat ID: " + chatId);
        } catch (IOException | InterruptedException e) {
            handleException("Error sending chat action", e);
        }
    }

    /**
     * Constructs an API URL with optional parameters.
     */
    private @NotNull String buildApiUrl(String endpoint, String @NotNull ... parameters) {
        StringBuilder url = new StringBuilder(TELEGRAM_API_URL).append(botToken).append("/").append(endpoint);
        if (parameters.length > 0) {
            url.append("?").append(String.join("&", parameters));
        }
        return url.toString();
    }

    /**
     * Sends a GET request.
     */
    private HttpResponse<String> sendRequest(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Logs the response from Telegram API.
     */
    private void logResponse(@NotNull HttpResponse<String> response, String successMessage) {
        if (response.statusCode() == 200) {
            logger.info("✅ " + successMessage);
        } else {
            logger.warning("❌ API call failed. Response: " + response.body());
        }
    }

    /**
     * Handles exceptions.
     */
    private void handleException(String message, @NotNull Exception e) {
        logger.severe("❌ " + message + ": " + e.getMessage());
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    public List<TelegramBotInfo> fetchUpdates() {
        String url = buildApiUrl("getUpdates");

        try {
            HttpResponse<String> responseJson = sendRequest(url);
            ObjectMapper mapper = new ObjectMapper();

            JsonNode updates0 = mapper.readValue(responseJson.body(), JsonNode.class);
            if (updates0.has("result")) {
                List<TelegramBotInfo> updates = new ArrayList<>();
                for (JsonNode update : updates0.get("result")) {
                    TelegramBotInfo botInfo = mapper.readValue(update.traverse(), TelegramBotInfo.class);
                    updates.add(botInfo);
                }
                return updates;
            }

        } catch (IOException e) {
            handleException("Error fetching updates", e);
            return Collections.emptyList();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return Collections.emptyList();
    }
}
