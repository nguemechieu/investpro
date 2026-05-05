package org.investpro.investpro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Telegram bot client with lightweight validation and graceful no-op behavior
 * when alerts are not fully configured.
 */
@Getter
@Setter
public class TelegramClient {
    private static final String TELEGRAM_API_URL = "https://api.telegram.org/bot";
    private static final Logger logger = LoggerFactory.getLogger(TelegramClient.class);

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String botToken;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private String username = "N/A";
    private boolean isOnline;
    private List<TelegramBotInfo> updatedData = new ArrayList<>();
    private long chatId;
    private int lastUpdateId = 0;
    private boolean alertsDisabledLogged;
    private boolean missingChatLogged;

    public TelegramClient(String telegramBotToken) {
        this.botToken = telegramBotToken == null ? "" : telegramBotToken.trim();
        if (botToken.isBlank()) {
            logger.info("Telegram bot token is not configured. Telegram alerts are disabled.");
            return;
        }

        this.isOnline = checkBotStatus();
        if (!isOnline) {
            return;
        }

        updatedData = fetchUpdates();
        TelegramBotInfo latestChat = updatedData.stream()
                .filter(info -> info.getChatId() != 0)
                .reduce((first, second) -> second)
                .orElse(null);

        if (latestChat != null) {
            username = latestChat.getUsername() == null || latestChat.getUsername().isBlank()
                    ? username
                    : latestChat.getUsername();
            chatId = latestChat.getChatId();
            logger.info("Telegram bot is ready for chat {}", chatId);
        } else {
            logger.warn("Telegram bot is reachable, but no chat id was discovered yet. Send a message to the bot to enable alerts.");
        }
    }

    private boolean checkBotStatus() {
        if (botToken.isBlank()) {
            return false;
        }

        String urlString = TELEGRAM_API_URL + botToken + "/getMe";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlString))
                .GET()
                .build();

        try {
            HttpResponse<String> responseJson = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode response = objectMapper.readTree(responseJson.body());
            if (!response.path("ok").asBoolean(false)) {
                logger.warn("Telegram getMe failed. Response: {}", responseJson.body());
                return false;
            }

            JsonNode result = response.path("result");
            username = result.path("username").asText("N/A");
            return result.path("id").asLong(0) > 0;
        } catch (IOException e) {
            handleException("Error checking bot status", e);
            return false;
        } catch (InterruptedException e) {
            handleException("Error checking bot status", e);
            return false;
        }
    }

    public void sendMessage(long chatId, String message) {
        long targetChatId = chatId != 0 ? chatId : this.chatId;
        if (!canSend(targetChatId, message)) {
            return;
        }

        try {
            sendChatActionInternal(targetChatId, ENUM_CHAT_ACTION.typing);
            String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);
            String url = buildApiUrl(
                    "sendMessage",
                    "chat_id=" + targetChatId,
                    "text=" + encodedMessage,
                    "parse_mode=Markdown"
            );
            HttpResponse<String> response = sendRequest(url);
            logResponse(response, "Message sent to chat ID: " + targetChatId);
        } catch (IOException | InterruptedException e) {
            handleException("Error sending message", e);
        }
    }

    public void sendChatAction(String chatId, ENUM_CHAT_ACTION action) {
        long targetChatId;
        try {
            targetChatId = Long.parseLong(chatId);
        } catch (NumberFormatException ex) {
            logMissingChatOnce();
            return;
        }

        if (!canSend(targetChatId, action.name())) {
            return;
        }

        try {
            sendChatActionInternal(targetChatId, action);
        } catch (IOException | InterruptedException e) {
            handleException("Error sending chat action", e);
        }
    }

    public boolean canSendMessages() {
        return isOnline && chatId != 0;
    }

    private boolean canSend(long targetChatId, String payload) {
        if (!isOnline) {
            logAlertsDisabledOnce();
            return false;
        }
        if (payload == null || payload.isBlank()) {
            return false;
        }
        if (targetChatId == 0) {
            logMissingChatOnce();
            return false;
        }
        return true;
    }

    private void sendChatActionInternal(long chatId, ENUM_CHAT_ACTION action) throws IOException, InterruptedException {
        String url = buildApiUrl("sendChatAction", "chat_id=" + chatId, "action=" + action);
        HttpResponse<String> response = sendRequest(url);
        logResponse(response, "Chat action '" + action + "' sent to chat ID: " + chatId);
    }

    private @NotNull String buildApiUrl(String endpoint, String @NotNull ... parameters) {
        StringBuilder url = new StringBuilder(TELEGRAM_API_URL).append(botToken).append("/").append(endpoint);
        if (parameters.length > 0) {
            url.append("?").append(String.join("&", parameters));
        }
        return url.toString();
    }

    private HttpResponse<String> sendRequest(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void logResponse(@NotNull HttpResponse<String> response, String successMessage) {
        String responseBody = response.body();

        try {
            JsonNode payload = objectMapper.readTree(responseBody);
            if (payload.path("ok").asBoolean(false)) {
                logger.debug(successMessage);
                return;
            }

            String description = payload.path("description").asText(responseBody);
            if (description.toLowerCase(Locale.ROOT).contains("chat not found")) {
                chatId = 0;
                logMissingChatOnce();
                return;
            }

            logger.warn("Telegram API call failed. Response: {}", responseBody);
        } catch (IOException ex) {
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.debug(successMessage);
            } else {
                logger.warn("Telegram API call failed. Response: {}", responseBody);
            }
        }
    }

    private void handleException(String message, @NotNull Exception e) {
        logger.error("{}: {}", message, e.getMessage(), e);
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    public List<TelegramBotInfo> fetchUpdates() {
        String url = buildApiUrl("getUpdates");

        try {
            HttpResponse<String> responseJson = sendRequest(url);
            JsonNode updates = objectMapper.readTree(responseJson.body());
            if (!updates.path("ok").asBoolean(false) || !updates.has("result")) {
                return Collections.emptyList();
            }

            List<TelegramBotInfo> results = new ArrayList<>();
            for (JsonNode update : updates.get("result")) {
                TelegramBotInfo botInfo = new TelegramBotInfo();
                botInfo.setUpdateId(update.path("update_id").asInt(lastUpdateId));
                botInfo.setChatId(extractChatId(update));
                botInfo.setUsername(extractUsername(update));
                results.add(botInfo);
            }

            if (!results.isEmpty()) {
                lastUpdateId = results.get(results.size() - 1).getUpdateId();
            }

            return results;
        } catch (IOException e) {
            handleException("Error fetching updates", e);
            return Collections.emptyList();
        } catch (InterruptedException e) {
            handleException("Error fetching updates", e);
            return Collections.emptyList();
        }
    }

    private long extractChatId(JsonNode update) {
        long extractedChatId = update.path("message").path("chat").path("id").asLong(0);
        if (extractedChatId != 0) {
            return extractedChatId;
        }

        extractedChatId = update.path("edited_message").path("chat").path("id").asLong(0);
        if (extractedChatId != 0) {
            return extractedChatId;
        }

        extractedChatId = update.path("channel_post").path("chat").path("id").asLong(0);
        if (extractedChatId != 0) {
            return extractedChatId;
        }

        extractedChatId = update.path("my_chat_member").path("chat").path("id").asLong(0);
        if (extractedChatId != 0) {
            return extractedChatId;
        }

        return update.path("callback_query").path("message").path("chat").path("id").asLong(0);
    }

    private String extractUsername(JsonNode update) {
        String extractedUsername = update.path("message").path("from").path("username").asText("");
        if (!extractedUsername.isBlank()) {
            return extractedUsername;
        }

        extractedUsername = update.path("edited_message").path("from").path("username").asText("");
        if (!extractedUsername.isBlank()) {
            return extractedUsername;
        }

        extractedUsername = update.path("callback_query").path("from").path("username").asText("");
        if (!extractedUsername.isBlank()) {
            return extractedUsername;
        }

        return username;
    }

    private void logAlertsDisabledOnce() {
        if (!alertsDisabledLogged) {
            alertsDisabledLogged = true;
            logger.warn("Telegram alerts are disabled because the bot is not configured or is offline.");
        }
    }

    private void logMissingChatOnce() {
        if (!missingChatLogged) {
            missingChatLogged = true;
            logger.warn("Telegram chat is not configured. Send a message to the bot first so InvestPro can discover a valid chat id.");
        }
    }
}
