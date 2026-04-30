package org.investpro.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Contract;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Telegram notifier for InvestPro.
 *
 * Features:
 * - auto-detects chat_id/channel_id from getUpdates
 * - supports explicit chat id or @channelusername
 * - sends text messages
 * - sends photos
 * - sends documents
 * - multi-user bot with ChatGPT integration for intelligent responses
 * - handles queries about market, news, trades, positions, orders, risk management
 * - processes order comments from multiple users
 *
 * Important:
 * Auto-detection works only after the bot receives an update.
 *
 * For private chat:
 * - Open the bot in Telegram
 * - Press Start or send any message
 *
 * For group:
 * - Add bot to the group
 * - Send a message in the group
 *
 * For channel:
 * - Add bot as channel admin
 * - Either use explicit @channelusername or make sure updates reach the bot
 */
@Getter
@Setter
public class TelegramNotifier {

    private static final Logger logger = LoggerFactory.getLogger(TelegramNotifier.class);

    private static final String TELEGRAM_API_BASE = "https://api.telegram.org/bot";
    private static final String OPENAI_API_BASE = "https://api.openai.com/v1";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String botToken;
    private final HttpClient httpClient;

    private volatile String chatId;
    private volatile long lastUpdateId = -1L;
    
    // Multi-user support
    private final Map<String, UserContext> userContexts = new ConcurrentHashMap<>();
    private volatile String openaiApiKey;
    private volatile boolean chatgptEnabled = false;
    private BiConsumer<String, String> orderCommentHandler;

    public TelegramNotifier(String botToken) {
        this(botToken, "");
    }

    public TelegramNotifier(String botToken, String chatIdOrChannelId) {
        this.botToken = safe(botToken);
        this.chatId = normalizeChatId(chatIdOrChannelId);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public boolean isEnabled() {
        return !botToken.isBlank();
    }

    public boolean hasTargetChat() {
        return chatId != null && !chatId.isBlank();
    }

    public void setChatId(String chatIdOrChannelId) {
        this.chatId = normalizeChatId(chatIdOrChannelId);
    }

    /**
     * Detect the latest chat/channel ID from getUpdates and use it as target.
     *
     * Returns empty if no usable chat was found.
     */
    public Optional<String> detectAndUseLatestChatId() {
        Set<String> detected = detectChatIds();

        if (detected.isEmpty()) {
            logger.warn("Telegram chat detection found no chats. Send /start to the bot or add it to a group/channel first.");
            return Optional.empty();
        }

        String detectedChatId = detected.iterator().next();
        this.chatId = detectedChatId;

        logger.info("Telegram target chat_id detected and selected: {}", detectedChatId);
        return Optional.of(detectedChatId);
    }

    /**
     * Detect all chat IDs visible to this bot from getUpdates.
     */
    public Set<String> detectChatIds() {
        Set<String> chatIds = new LinkedHashSet<>();

        if (!isEnabled()) {
            logger.warn("Telegram bot token is empty. Cannot detect chat IDs.");
            return chatIds;
        }

        String url = apiUrl("getUpdates");

        if (lastUpdateId >= 0) {
            url += "?offset=%d".formatted(lastUpdateId + 1);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                logger.warn("Telegram getUpdates failed HTTP {}: {}", response.statusCode(), response.body());
                return chatIds;
            }

            JsonNode root = OBJECT_MAPPER.readTree(response.body());

            if (!root.path("ok").asBoolean(false)) {
                logger.warn("Telegram getUpdates returned not ok: {}", response.body());
                return chatIds;
            }

            ArrayNode result = root.withArray("result");

            for (JsonNode update : result) {
                long updateId = update.path("update_id").asLong(-1L);
                if (updateId > lastUpdateId) {
                    lastUpdateId = updateId;
                }

                Optional<String> id = extractChatId(update);

                id.ifPresent(chatIds::add);
            }
        } catch (IOException exception) {
            logger.warn("Telegram chat detection IO error", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.warn("Telegram chat detection interrupted", exception);
        } catch (Exception exception) {
            logger.warn("Telegram chat detection failed", exception);
        }

        return chatIds;
    }

    /**
     * Send plain text to the configured chat.
     * If chat id is missing, it tries to auto-detect it once.
     */
    public boolean send(String message) {
        return sendMessage(message);
    }

    public boolean sendMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }

        Optional<String> target = resolveTargetChatId();

        if (target.isEmpty()) {
            logger.warn("Telegram message skipped because no chat_id/channel_id is available.");
            return false;
        }

        String body = "chat_id=%s&text=%s&parse_mode=Markdown".formatted(
                encode(target.get()),
                encode(escapeMarkdown(message))
        );



        return postForm("sendMessage", body);
    }

    public boolean sendMarkdown(String markdownMessage) {
        if (markdownMessage == null || markdownMessage.isBlank()) {
            return false;
        }

        Optional<String> target = resolveTargetChatId();

        if (target.isEmpty()) {
            logger.warn("Telegram markdown message skipped because no chat_id/channel_id is available.");
            return false;
        }

        String body = "chat_id=%s&text=%s&parse_mode=Markdown".formatted(
                encode(target.get()),
                encode(markdownMessage)
        );

        return postForm("sendMessage", body);
    }

    public boolean sendHtml(String htmlMessage) {
        if (htmlMessage == null || htmlMessage.isBlank()) {
            return false;
        }

        Optional<String> target = resolveTargetChatId();

        if (target.isEmpty()) {
            logger.warn("Telegram HTML message skipped because no chat_id/channel_id is available.");
            return false;
        }

        String body = "chat_id=%s&text=%s&parse_mode=HTML".formatted(
                encode(target.get()),
                encode(htmlMessage)
        );

        return postForm("sendMessage", body);
    }

    /**
     * Send a photo by local path.
     */
    public boolean sendPhoto(Path photoPath, String caption) {
        if (photoPath == null || !Files.exists(photoPath)) {
            logger.warn("Telegram photo skipped because file does not exist: {}", photoPath);
            return false;
        }

        Optional<String> target = resolveTargetChatId();

        if (target.isEmpty()) {
            if (logger.isWarnEnabled())
                logger.warn("""
                        Telegram photo skipped because no chat_id/channel_id is available.""");
            return false;
        }

        try {
            return postMultipart(
                    "sendPhoto",
                    target.get(),
                    "photo",
                    photoPath,
                    caption
            );
        } catch (Exception exception) {
            logger.warn("Telegram sendPhoto failed", exception);
            return false;
        }
    }

    /**
     * Send a photo by public URL or Telegram file_id.
     */
    public boolean sendPhoto(String photoUrlOrFileId, String caption) {
        if (photoUrlOrFileId == null || photoUrlOrFileId.isBlank()) {
            return false;
        }

        Optional<String> target = resolveTargetChatId();

        if (target.isEmpty()) {
            logger.warn("Telegram photo skipped because no chat_id/channel_id is available.");
            return false;
        }

        String body = "chat_id=%s&photo=%s&caption=%s&parse_mode=Markdown".formatted(
                encode(target.get()),
                encode(photoUrlOrFileId),
                encode(escapeMarkdown(safe(caption)))
        );

        return postForm("sendPhoto", body);
    }

    /**
     * Send a document by local path.
     */
    public boolean sendDocument(Path documentPath, String caption) {
        if (documentPath == null || !Files.exists(documentPath)) {
            logger.warn("Telegram document skipped because file does not exist: {}", documentPath);
            return false;
        }

        Optional<String> target = resolveTargetChatId();

        if (target.isEmpty()) {
            if (logger.isWarnEnabled())
                logger.warn("Telegram document skipped because no chat_id/channel_id is available.");
            return false;
        }

        try {
            return postMultipart(
                    "sendDocument",
                    target.get(),
                    "document",
                    documentPath,
                    caption
            );
        } catch (Exception exception) {
            logger.warn("Telegram sendDocument failed", exception);
            return false;
        }
    }

    /**
     * Send a document by public URL or Telegram file_id.
     */
    public boolean sendDocument(String documentUrlOrFileId, String caption) {
        if (documentUrlOrFileId == null || documentUrlOrFileId.isBlank()) {
            return false;
        }

        Optional<String> target = resolveTargetChatId();

        if (target.isEmpty()) {
            logger.warn("Telegram document skipped because no chat_id/channel_id is available.");
            return false;
        }

        String body = "chat_id=%s&document=%s&caption=%s&parse_mode=Markdown".formatted(
                encode(target.get()),
                encode(documentUrlOrFileId),
                encode(escapeMarkdown(safe(caption)))
        );

        return postForm("sendDocument", body);
    }

    private Optional<String> resolveTargetChatId() {
        if (!isEnabled()) {
            logger.warn("Telegram bot token is empty.");
            return Optional.empty();
        }

        if (hasTargetChat()) {
            return Optional.of(chatId);
        }

        return detectAndUseLatestChatId();
    }

    private Optional<String> extractChatId(JsonNode update) {
        if (update == null || update.isMissingNode()) {
            return Optional.empty();
        }

        /*
         * Common update locations where chat info can appear.
         */
        String[] paths = {
                "/message/chat/id",
                "/edited_message/chat/id",
                "/channel_post/chat/id",
                "/edited_channel_post/chat/id",
                "/my_chat_member/chat/id",
                "/chat_member/chat/id",
                "/callback_query/message/chat/id"
        };

        for (String path : paths) {
            JsonNode value = update.at(path);

            if (!value.isMissingNode() && !value.isNull()) {
                String id = value.asText("").trim();

                if (!id.isBlank()) {
                    return Optional.of(id);
                }
            }
        }

        /*
         * If a public channel username is visible, sending by @username can work.
         */
        String[] usernamePaths = {
                "/channel_post/chat/username",
                "/message/chat/username"
        };

        for (String path : usernamePaths) {
            JsonNode value = update.at(path);

            if (!value.isMissingNode() && !value.isNull()) {
                String username = value.asText("").trim();

                if (!username.isBlank()) {
                    return Optional.of("@%s".formatted(username.replace("@", "")));
                }
            }
        }

        return Optional.empty();
    }

    private boolean postForm(String method, String body) {
        if (!isEnabled()) {
            return false;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl(method)))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return sendRequest(request, method);
    }

    private boolean postMultipart(
            String method,
            String chatId,
            String fileFieldName,
            Path filePath,
            String caption
    ) throws IOException {
        String boundary = "----InvestProTelegramBoundary%d".formatted(Instant.now().toEpochMilli());

        byte[] body = buildMultipartBody(
                boundary,
                chatId,
                fileFieldName,
                filePath,
                caption
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl(method)))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "multipart/form-data; boundary=%s".formatted(boundary))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        return sendRequest(request, method);
    }

    private byte[] buildMultipartBody(
            String boundary,
            String chatId,
            String fileFieldName,
            Path filePath,
            String caption
    ) throws IOException {
        String fileName = filePath.getFileName().toString();
        String contentType = detectContentType(filePath);

        byte[] fileBytes = Files.readAllBytes(filePath);

        StringBuilder prefix = new StringBuilder();

        appendFormField(prefix, boundary, "chat_id", chatId);

        if (caption != null && !caption.isBlank()) {
            appendFormField(prefix, boundary, "caption", caption);
            appendFormField(prefix, boundary, "parse_mode", "Markdown");
        }

        prefix.append("--").append(boundary).append("\r\n");
        prefix.append("Content-Disposition: form-data; name=\"")
                .append(fileFieldName)
                .append("\"; filename=\"")
                .append(fileName.replace("\"", ""))
                .append("\"\r\n");
        prefix.append("Content-Type: ").append(contentType).append("\r\n\r\n");

        String suffix = "\r\n--%s--\r\n".formatted(boundary);

        byte[] prefixBytes = prefix.toString().getBytes(StandardCharsets.UTF_8);
        byte[] suffixBytes = suffix.getBytes(StandardCharsets.UTF_8);

        byte[] body = new byte[prefixBytes.length + fileBytes.length + suffixBytes.length];

        System.arraycopy(prefixBytes, 0, body, 0, prefixBytes.length);
        System.arraycopy(fileBytes, 0, body, prefixBytes.length, fileBytes.length);
        System.arraycopy(suffixBytes, 0, body, prefixBytes.length + fileBytes.length, suffixBytes.length);

        return body;
    }

    private void appendFormField(StringBuilder builder, String boundary, String name, String value) {
        builder.append("--").append(boundary).append("\r\n");
        builder.append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n");
        builder.append(value == null ? "" : value).append("\r\n");
    }

    private boolean sendRequest(HttpRequest request, String method) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                logger.warn("Telegram {} failed HTTP {}: {}", method, response.statusCode(), response.body());
                return false;
            }

            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            boolean ok = root.path("ok").asBoolean(false);

            if (!ok) {
                logger.warn("Telegram {} returned not ok: {}", method, response.body());
            }

            return ok;
        } catch (IOException exception) {
            logger.warn("Telegram {} IO error", method, exception);
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.warn("Telegram {} interrupted", method, exception);
            return false;
        } catch (Exception exception) {
            logger.warn("Telegram {} failed", method, exception);
            return false;
        }
    }

    @Contract(pure = true)
    private @NotNull String apiUrl(String method) {
        return "%s%s/%s".formatted(TELEGRAM_API_BASE, botToken, method);
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private @NotNull String normalizeChatId(String value) {
        String text = safe(value);

        if (text.isBlank()) {
            return "";
        }

        /*
         * Allow:
         * - numeric chat id: 123456789
         * - supergroup/channel id: -1001234567890
         * - channel username: @my_channel
         */
        return text;


    }

    private String detectContentType(Path path) {
        try {
            String type = Files.probeContentType(path);

            if (type != null && !type.isBlank()) {
                return type;
            }
        } catch (IOException ignored) {
            // fallback below
        }

        String name = path.getFileName().toString().toLowerCase();

        if (name.endsWith(".png")) {
            return "image/png";
        }

        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }

        if (name.endsWith(".gif")) {
            return "image/gif";
        }

        if (name.endsWith(".pdf")) {
            return "application/pdf";
        }

        if (name.endsWith(".csv")) {
            return "text/csv";
        }

        if (name.endsWith(".txt")) {
            return "text/plain";
        }

        if (name.endsWith(".json")) {
            return "application/json";
        }

        return "application/octet-stream";
    }

    private @NotNull String escapeMarkdown(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("_", "\\_")
                .replace("*", "\\*")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("`", "\\`");
    }

    @Contract(pure = true)
    private @NotNull String safe(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Initialize ChatGPT integration for intelligent bot responses.
     * Bot will use ChatGPT to answer questions about market, news, trades, positions, orders, etc.
     *
     * @param apiKey the OpenAI API key
     */
    public void initializeChatGPT(String apiKey) {
        if (apiKey != null && !apiKey.isBlank()) {
            this.openaiApiKey = apiKey.trim();
            this.chatgptEnabled = true;
            logger.info("ChatGPT integration initialized for multi-user bot");
        }
    }

    /**
     * Process incoming messages from multiple users.
     * Polls for new messages and processes them based on user queries.
     * Supports: market info, news, trade queries, positions, orders, risk management, profitability questions.
     */
    public void pollAndProcessUserMessages() {
        if (!isEnabled()) {
            logger.warn("Cannot poll messages: bot token not configured");
            return;
        }

        try {
            Set<String> chatIds = detectChatIds();

            for (String cid : chatIds) {
                Optional<UserMessage> message = getLatestUserMessage(cid);

                message.ifPresent(msg -> {
                    UserContext context = userContexts.computeIfAbsent(cid, k -> new UserContext(cid));
                    processUserMessage(context, msg);
                });
            }
        } catch (Exception e) {
            logger.warn("Error processing user messages", e);
        }
    }

    /**
     * Get latest message from a specific user/chat.
     */
    private Optional<UserMessage> getLatestUserMessage(String chatId) {
        try {
            String url = apiUrl("getUpdates") + "?chat_id=" + encode(chatId);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 400) {
                JsonNode root = OBJECT_MAPPER.readTree(response.body());
                ArrayNode updates = root.withArray("result");

                if (!updates.isEmpty()) {
                    JsonNode latestUpdate = updates.get(updates.size() - 1);
                    JsonNode messageNode = latestUpdate.path("message");

                    if (!messageNode.isMissingNode()) {
                        String text = messageNode.path("text").asText("");
                        String userId = messageNode.path("from").path("id").asText("");
                        String userName = messageNode.path("from").path("username").asText("User");
                        long msgTime = messageNode.path("date").asLong(0);

                        if (!text.isBlank() && !userId.isBlank()) {
                            return Optional.of(new UserMessage(userId, userName, chatId, text, msgTime));
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Error getting user message from chat {}", chatId, e);
        }

        return Optional.empty();
    }

    /**
     * Process a single user message and respond accordingly.
     */
    private void processUserMessage(UserContext context, UserMessage message) {
        logger.info("Processing message from user {} ({}): {}", message.userId, message.userName, message.text);

        String response ;

        // Check if it's an order comment
        if (message.text.startsWith("/comment")) {
            response = handleOrderComment(message);
        }
        // Check for specific query types
        else if (isMarketQuery(message.text)) {
            response = handleMarketQuery(message);
        } else if (isNewsQuery(message.text)) {
            response = handleNewsQuery(message);
        } else if (isTradeQuery(message.text)) {
            response = handleTradeQuery(message);
        } else if (isPositionQuery(message.text)) {
            response = handlePositionQuery(message);
        } else if (isOrderQuery(message.text)) {
            response = handleOrderQuery(message);
        } else if (isRiskQuery(message.text)) {
            response = handleRiskManagementQuery(message);
        } else if (isProfitabilityQuery(message.text)) {
            response = handleProfitabilityQuery(message);
        } else if (isLotQuery(message.text)) {
            response = handleLotQuery(message);
        } else {
            // Use ChatGPT if available for general questions
            response = handleGeneralQuery(message);
        }

        if (!response.isBlank()) {
            String replyText = "👤 *%s*: %s".formatted(message.userName, response);
            sendMessageToChat(message.chatId, replyText);
        }

        context.lastProcessedUpdate = message.timestamp;
    }

    /**
     * Send message to a specific chat.
     */
    private boolean sendMessageToChat(String targetChatId, String text) {
        String body = "chat_id=%s&text=%s&parse_mode=Markdown".formatted(
                encode(targetChatId),
                encode(escapeMarkdown(text))
        );
        return postForm("sendMessage", body);
    }

    /**
     * Handle order comment from user - /comment orderId some comment text.
     */
    private String handleOrderComment(UserMessage message) {
        String[] parts = message.text.split(" ", 3);

        if (parts.length < 3) {
            return "Usage: /comment <orderId> <your comment>";
        }

        String orderId = parts[1];
        String comment = parts[2];

        if (orderCommentHandler != null) {
            orderCommentHandler.accept(orderId, comment);
        }

        return "✅ Comment added to order " + orderId + ": " + comment;
    }

    /**
     * Handle market-related queries.
     */
    private String handleMarketQuery(UserMessage message) {
        return queryAI("Answer this market trading question concisely: " + message.text);
    }

    /**
     * Handle news-related queries.
     */
    private String handleNewsQuery(UserMessage message) {
        return queryAI("Provide market news insights for: " + message.text);
    }

    /**
     * Handle trade-related queries.
     */
    private String handleTradeQuery(UserMessage message) {
        return queryAI("Trading advice for: " + message.text);
    }

    /**
     * Handle position-related queries.
     */
    private String handlePositionQuery(UserMessage message) {
        return queryAI("Position management advice: " + message.text);
    }

    /**
     * Handle order-related queries.
     */
    private String handleOrderQuery(UserMessage message) {
        return queryAI("Order placement guidance: " + message.text);
    }

    /**
     * Handle risk management queries.
     */
    private String handleRiskManagementQuery(UserMessage message) {
        return queryAI("Risk management strategy for: " + message.text);
    }

    /**
     * Handle profitability-related queries.
     */
    private String handleProfitabilityQuery(UserMessage message) {
        return queryAI("Profitability optimization: " + message.text);
    }

    /**
     * Handle lot size queries.
     */
    private String handleLotQuery(UserMessage message) {
        return queryAI("Lot sizing calculation: " + message.text);
    }

    /**
     * Handle general questions with ChatGPT.
     */
    private String handleGeneralQuery(UserMessage message) {
        return queryAI(message.text);
    }

    /**
     * Query ChatGPT for intelligent responses.
     * Falls back to default response if ChatGPT is unavailable.
     */
    private String queryAI(String prompt) {
        if (!chatgptEnabled || openaiApiKey == null || openaiApiKey.isBlank()) {
            return "ℹ️ ChatGPT is not configured. Please set up OpenAI API key for intelligent responses.";
        }

        try {
            ObjectNode requestBody = OBJECT_MAPPER.createObjectNode();
            requestBody.put("model", "gpt-3.5-turbo");
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 500);

            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode sysMsg = messages.addObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", "You are a helpful trading and investment advisor. Provide concise, practical advice in under 100 words.");

            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);

            String body = OBJECT_MAPPER.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("%s/chat/completions".formatted(OPENAI_API_BASE)))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer %s".formatted(openaiApiKey))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode responseBody = OBJECT_MAPPER.readTree(response.body());
                String content = responseBody.path("choices")
                        .path(0)
                        .path("message")
                        .path("content")
                        .asText("");

                return content.isBlank() ? "No response from ChatGPT" : content;
            } else {
                logger.warn("ChatGPT API error: HTTP {}", response.statusCode());
                return "⚠️ ChatGPT returned an error. Using default response.";
            }
        } catch (Exception e) {
            logger.warn("Error querying ChatGPT", e);
            return "⚠️ Could not reach ChatGPT. Please try again later.";
        }
    }

    /**
     * Check if message is a market query.
     */
    private boolean isMarketQuery(String text) {
        return text.toLowerCase().matches(".*(market|price|chart|trend|analysis|btc|eth|forex).*");
    }

    /**
     * Check if message is a news query.
     */
    private boolean isNewsQuery(String text) {
        return text.toLowerCase().matches(".*(news|headline|event|announcement|breaking).*");
    }

    /**
     * Check if message is a trade query.
     */
    private boolean isTradeQuery(String text) {
        return text.toLowerCase().matches(".*(trade|entry|exit|long|short|reversal).*");
    }

    /**
     * Check if message is a position query.
     */
    private boolean isPositionQuery(String text) {
        return text.toLowerCase().matches(".*(position|holding|exposure|portfolio|allocation).*");
    }

    /**
     * Check if message is an order query.
     */
    private boolean isOrderQuery(String text) {
        return text.toLowerCase().matches(".*(order|limit|market|stop|tp|sl).*");
    }

    /**
     * Check if message is a risk management query.
     */
    private boolean isRiskQuery(String text) {
        return text.toLowerCase().matches(".*(risk|stop loss|hedge|drawdown|margin|leverage).*");
    }

    /**
     * Check if message is a profitability query.
     */
    private boolean isProfitabilityQuery(String text) {
        return text.toLowerCase().matches(".*(profit|loss|roi|return|performance|pnl).*");
    }

    /**
     * Check if message is a lot size query.
     */
    private boolean isLotQuery(String text) {
        return text.toLowerCase().matches(".*(lot|size|volume|quantity|units).*");
    }

    /**
     * Set handler for order comments from users.
     * Example: (orderId, comment) -> db.updateOrderComment(orderId, comment)
     */
    public void setOrderCommentHandler(BiConsumer<String, String> handler) {
        this.orderCommentHandler = handler;
    }

    /**
     * User context for tracking conversation state.
     */
    @Getter
    private static class UserContext {
        private final String userId;
        private long lastProcessedUpdate;

        UserContext(String userId) {
            this.userId = userId;
            this.lastProcessedUpdate = System.currentTimeMillis();
        }
    }

    /**
         * User message container.
         */
        private record UserMessage(String userId, String userName, String chatId, String text, long timestamp) {
    }
}