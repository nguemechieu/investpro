package org.investpro;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Logger;

/**
 * Enhanced Telegram Bot Client with support for messages, media, files, chat actions, and status checks.
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

    String chatId;
    /**
     * Sends a text message (supports emojis).
     *
     * @param chatId  The chat ID or username
     * @param message The message to send
     */
    public void sendMessage(String chatId, String message) {
        try {
            sendChatAction(chatId, ENUM_CHAT_ACTION.typing);
            String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);
            String url = buildApiUrl("sendMessage", "chat_id=" + chatId, "text=" + encodedMessage, "parse_mode=Markdown");

            HttpResponse<String> response = sendRequest(url);
            logResponse(response, "Message sent to chat ID: " + chatId);
        } catch (IOException | InterruptedException e) {
            handleException("Error sending message", e);
        }
    }

    /**
     * Sends a photo.
     *
     * @param chatId    The chat ID
     * @param photoPath Path to the photo file
     */
    public void sendPhoto(String chatId, String photoPath) {
        try {
            sendChatAction(chatId, ENUM_CHAT_ACTION.upload_photo);
            File photo = new File(photoPath);
            if (!photo.exists()) {
                logger.warning("Photo file not found: " + photoPath);
                return;
            }

            HttpResponse<String> response = sendMultipartRequest("sendPhoto", chatId, "photo", photo);
            logResponse(response, "Photo sent to chat ID: " + chatId);
        } catch (IOException | InterruptedException e) {
            handleException("Error sending photo", e);
        }
    }

    /**
     * Sends a document or file.
     *
     * @param chatId   The chat ID
     * @param filePath Path to the document/file
     */
    public void sendDocument(String chatId, String filePath) {
        try {
            sendChatAction(chatId, ENUM_CHAT_ACTION.upload_document);
            File file = new File(filePath);
            if (!file.exists()) {
                logger.warning("File not found: " + filePath);
                return;
            }

            HttpResponse<String> response = sendMultipartRequest("sendDocument", chatId, "document", file);
            logResponse(response, "Document sent to chat ID: " + chatId);
        } catch (IOException | InterruptedException e) {
            handleException("Error sending document", e);
        }
    }

    /**
     * Sends a chat action (e.g., "typing", "upload_photo").
     *
     * @param chatId The chat ID
     * @param action The action type (e.g., "typing", "upload_photo", "record_video", etc.)
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
     * Checks if the bot is online.
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
     * @return The bot's username
     */
    private @Nullable String fetchBotUsername() {
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
     * Sends a multipart/form-data request (for files, images, etc.).
     */
    private HttpResponse<String> sendMultipartRequest(String method, String chatId, String fileType, @NotNull File file) throws IOException, InterruptedException {
        String boundary = "------WebKitFormBoundary" + System.currentTimeMillis();
        String url = TELEGRAM_API_URL + botToken + "/" + method;

        byte[] fileBytes = Files.readAllBytes(file.toPath());
        String fileName = file.getName();

        String requestBody = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n" + chatId + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + fileType + "\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";

        byte[] finalBody = (requestBody + new String(fileBytes, StandardCharsets.UTF_8) + "\r\n--" + boundary + "--").getBytes();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(finalBody))
                .build();

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

    public void run() {
        while (!Thread.interrupted()) {
            try {
                // Example usage:
                sendMessage(chatId,
                        "Hello, this is a test message from your Java Telegram Bot API client.\n" +
                                "You can customize this message according to your needs.");
                sendLocation(chatId, 40.7128, -74.0060);
                sendPhoto(chatId, "path/to/your/photo.jpg");
                sendDocument(chatId, "path/to/your/document.pdf");
                sendChatAction(chatId, ENUM_CHAT_ACTION.upload_photo);

                if (!checkBotStatus()) {
                    logger.severe("Bot is offline. Exiting...");
                    break;
                }

                Thread.sleep(10000); // Sleep for 10 seconds before making another API call
            } catch (InterruptedException e) {
                handleException("Error in bot thread", e);
            }
        }
    }

    private void sendLocation(String chatId, double v, double v1) {
        try {
            String url = buildApiUrl("sendLocation", "chat_id=" + chatId, "latitude=" + v, "longitude=" + v1);
            HttpResponse<String> response = sendRequest(url);
            logResponse(response, "Location sent to chat ID: " + chatId);
        } catch (IOException | InterruptedException e) {
            handleException("Error sending location", e);
        }
    }
}
