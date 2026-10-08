package org.investpro.core;

import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.investpro.utils.ENUM_CHAT_ACTION;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Telegram notifier for InvestPro.
 * <p>
 * Features:
 * <ul>
 *   <li>Autodetects chat_id/channel_id from {@code getUpdates}</li>
 *   <li>Verifies the preferred notification destination against authorized API updates</li>
 *   <li>Sends text messages</li>
 *   <li>Sends photos</li>
 *   <li>Sends documents</li>
 *   <li>Multiuser bot with ChatGPT integration for intelligent responses</li>
 *   <li>Handles queries about market, news, trades, positions, orders, risk management</li>
 *   <li>Processes order comments from multiple users</li>
 * </ul>
 * <p>
 * <b>Important:</b> Auto-detection works only after the bot receives an update.
 * <p>
 * For private chat: open the bot in Telegram and press Start or send any message.
 * <p>
 * Assistant replies require an authorized private-chat user. Each reply uses that
 * incoming message's numeric chat ID, independently of the notification destination.
 */
@Getter
@Setter
@Slf4j
public class TelegramNotifier {
    private static final String TELEGRAM_API_BASE = "https://api.telegram.org/bot";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String NO_CHAT_ID_LOG = "Telegram {} skipped: no chat_id configured.";

    private final String botToken;
    private final HttpClient httpClient;
    private final org.investpro.ai.InvestorAssistantService assistantService;

    private volatile String chatId;
    private volatile String preferredChatId = "";
    private volatile String discoveredChatId;
    private volatile long lastUpdateId = -1L;

    // Multi-user support
    private final Map<String, UserContext> userContexts = new ConcurrentHashMap<>();
    private final Set<String> allowedUsers = ConcurrentHashMap.newKeySet();
    private final Set<String> allowedChats = ConcurrentHashMap.newKeySet();
    private volatile Function<String, String> questionContext = question -> "";
    private final Set<String> pendingConversations = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor questionWorkers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), r -> {
                Thread thread = new Thread(r, "TelegramQuestion"); thread.setDaemon(true); return thread;
            });
    private volatile String openaiModel = "gpt-4.1-mini";
    private volatile String openaiApiKey;
    private volatile boolean chatgptEnabled = false;
    private BiConsumer<String, String> orderCommentHandler;
    private volatile TelegramCommandHandler commandHandler;
    /**
     * -- GETTER --
     *  Check if polling is currently enabled
     */
    private volatile boolean pollingEnabled = false;
    private volatile Thread pollingThread;

    // Guard against concurrent getUpdates calls (Telegram HTTP 409)
    private final AtomicBoolean getUpdatesInFlight = new AtomicBoolean(false);
    private volatile long lastDetectionAttemptMs = 0L;
    private volatile long lastDetectionWarningMs = 0L;
    private volatile boolean lastDetectionFailed = false;
    private static final long DETECTION_MIN_INTERVAL_MS = 30_000L; // 30 s cooldown
    private static final long DETECTION_WARNING_INTERVAL_MS = 120_000L;


    public TelegramNotifier(String botToken) {
        this(botToken, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build());
    }

    TelegramNotifier(String botToken, HttpClient httpClient) {
        this.botToken = safe(botToken);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.assistantService = new org.investpro.ai.InvestorAssistantService(httpClient);
    }

    public boolean isEnabled() {
        return !botToken.isBlank();
    }

    public boolean hasTargetChat() {
        return isValidChatId(chatId);
    }

    /**
     * Detect the latest chat/channel ID from {@code getUpdates} and use it as target.
     *
     * @return an {@link Optional} containing the detected chat ID, or empty if none found
     */
    public Optional<String> detectAndUseLatestChatId() {
        if (allowedUsers.isEmpty()) return Optional.empty();
        if (isValidChatId(discoveredChatId)) return Optional.of(discoveredChatId);
        detectChatIds();
        if (isValidChatId(discoveredChatId)) return Optional.of(discoveredChatId);
        // Only processUpdates may select an authorized chat observed in an API update.
        return Optional.empty();

    }

    /**
     * Detect all chat IDs visible to this bot from {@code getUpdates}.
     *
     * @return set of detected chat IDs
     */
    public Set<String> detectChatIds() {

        String url = apiUrl("getUpdates");

        Set<String> chatIds = new LinkedHashSet<>();

        if (!isEnabled()) {
            log.warn("Telegram bot token is empty. Cannot detect chat IDs.");
            return chatIds;
        }

        // Throttle: don't retry more often than once every 30 s
        long now = System.currentTimeMillis();
        if (now - lastDetectionAttemptMs < DETECTION_MIN_INTERVAL_MS) {
            log.debug("Telegram getUpdates skipped: cooldown active.");
            return chatIds;
        }

        // Allow only one thread at a time to call getUpdates (prevents HTTP 409)
        if (!getUpdatesInFlight.compareAndSet(false, true)) {
            log.debug("Telegram getUpdates skipped: another thread already polling.");
            return chatIds;
        }

        lastDetectionAttemptMs = now;
        lastDetectionFailed = false;
        try {
            if (lastUpdateId >= 0) {
                url += "?offset=%d".formatted(lastUpdateId + 1);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                log.warn("Telegram getUpdates failed HTTP {}: {}", response.statusCode(), response.body());
                return chatIds;
            }

            JsonNode root = OBJECT_MAPPER.readTree(response.body());

            if (!root.path("ok").asBoolean(false)) {
                log.warn("Telegram getUpdates returned not ok: {}", response.body());
                return chatIds;
            }

            ArrayNode result = root.withArray("result");

            for (JsonNode update : result) {
                Optional<String> id = extractChatId(update);
                id.ifPresent(chatIds::add);
            }
            // Discovery reads the same queue as polling: dispatch before acknowledging updates.
            processUpdates(root);
        } catch (IOException exception) {
            lastDetectionFailed = true;
            logTelegramDetectionFailure("IO error", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            lastDetectionFailed = true;
            logTelegramDetectionFailure("interrupted", exception);
        } catch (Exception exception) {
            lastDetectionFailed = true;
            logTelegramDetectionFailure("failed", exception);
        } finally {
            getUpdatesInFlight.set(false);
        }

        return chatIds;
    }

    private void logTelegramDetectionFailure(String category, Exception exception) {
        long now = System.currentTimeMillis();
        String message = rootMessage(exception);

        if (now - lastDetectionWarningMs >= DETECTION_WARNING_INTERVAL_MS) {
            lastDetectionWarningMs = now;
            log.warn("Telegram chat detection {}: {}. Configure TELEGRAM_CHAT_ID or check network/DNS.", category, message);
        } else {
            log.debug("Telegram chat detection {}: {}", category, message, exception);
        }
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
            log.debug(NO_CHAT_ID_LOG, "message");
            return false;
        }

        String body = "chat_id=%s&text=%s&parse_mode=Markdown".formatted(
                encode(target.get()),
                encode(escapeMarkdown(message)));

        return postForm("sendMessage", body);
    }

    public boolean sendMarkdown(String markdownMessage) {
        if (markdownMessage == null || markdownMessage.isBlank()) {
            return false;
        }

        Optional<String> target = resolveTargetChatId();

        if (target.isEmpty()) {
            log.debug(NO_CHAT_ID_LOG, "markdown message");
            return false;
        }

        String body = "chat_id=%s&text=%s&parse_mode=Markdown".formatted(
                encode(target.get()),
                encode(markdownMessage));

        return postForm("sendMessage", body);
    }

    @SuppressWarnings("unused")
    public boolean sendHtml(String htmlMessage) {
        if (htmlMessage == null || htmlMessage.isBlank()) {
            return false;
        }

        Optional<String> target = resolveTargetChatId();

        if (target.isEmpty()) {
            log.debug(NO_CHAT_ID_LOG, "HTML message");
            return false;
        }

        String body = "chat_id=%s&text=%s&parse_mode=HTML".formatted(
                encode(target.get()),
                encode(htmlMessage));

        return postForm("sendMessage", body);
    }

    /**
     * Send typing/action indicator to show the user that the bot is processing their request.
     * Useful for long-running operations to give visual feedback.
     */
    public void sendChatAction(String targetChatId, ENUM_CHAT_ACTION action) {
        if (!isValidChatId(targetChatId) || action == null) {
            return;
        }

        try {
            String actionValue = action.toString().toLowerCase();
            String body = "chat_id=%s&action=%s".formatted(
                    encode(targetChatId),
                    encode(actionValue));
            postForm("sendChatAction", body);
        } catch (Exception e) {
            log.debug("Error sending chat action: {}", e.getMessage());
        }
    }

    /**
     * Send a photo by local path.
     */
    @SuppressWarnings("unused")
    public boolean sendPhoto(Path photoPath, String caption) {
        if (photoPath == null || !Files.exists(photoPath)) {
            log.warn("Telegram photo skipped because file does not exist: {}", photoPath);
            return false;
        }

        Optional<String> target = resolveTargetChatId();

        if (target.isEmpty()) {
            log.warn(NO_CHAT_ID_LOG, "photo");
            return false;
        }

        return sendPhotoToChat(target.get(), photoPath, caption);
    }

    protected boolean sendPhotoToChat(String targetChatId, Path photoPath, String caption) {
        if (!isValidChatId(targetChatId)) return false;

        try {
            return postMultipart(
                    "sendPhoto",
                    targetChatId,
                    "photo",
                    photoPath,
                    caption);
        } catch (Exception exception) {
            log.warn("Telegram sendPhoto failed", exception);
            return false;
        }
    }

    /**
     * Send a photo by public URL or Telegram file_id.
     */
    @SuppressWarnings("unused")
    public boolean sendPhoto(String photoUrlOrFileId, String caption) {
        if (photoUrlOrFileId == null || photoUrlOrFileId.isBlank()) {
            return false;
        }

        Optional<String> target = resolveTargetChatId();

        if (target.isEmpty()) {
            log.warn(NO_CHAT_ID_LOG, "photo");
            return false;
        }

        String body = "chat_id=%s&photo=%s&caption=%s&parse_mode=Markdown".formatted(
                encode(target.get()),
                encode(photoUrlOrFileId),
                encode(escapeMarkdown(safe(caption))));

        return postForm("sendPhoto", body);
    }

    /**
     * Send a document by local path.
     */
    @SuppressWarnings("unused")
    public boolean sendDocument(Path documentPath, String caption) {
        if (documentPath == null || !Files.exists(documentPath)) {
            log.warn("Telegram document skipped because file does not exist: {}", documentPath);
            return false;
        }

        Optional<String> target = resolveTargetChatId();

        if (target.isEmpty()) {
            log.warn(NO_CHAT_ID_LOG, "document");
            return false;
        }

        try {
            return postMultipart(
                    "sendDocument",
                    target.get(),
                    "document",
                    documentPath,
                    caption);
        } catch (Exception exception) {
            log.warn("Telegram sendDocument failed", exception);
            return false;
        }
    }

    /**
     * Send a document by public URL or Telegram file_id.
     */
    @SuppressWarnings("unused")
    public boolean sendDocument(String documentUrlOrFileId, String caption) {
        if (documentUrlOrFileId == null || documentUrlOrFileId.isBlank()) {
            return false;
        }

        Optional<String> target = resolveTargetChatId();

        if (target.isEmpty()) {
            log.warn(NO_CHAT_ID_LOG, "document");
            return false;
        }

        String body = "chat_id=%s&document=%s&caption=%s&parse_mode=Markdown".formatted(
                encode(target.get()),
                encode(documentUrlOrFileId),
                encode(escapeMarkdown(safe(caption))));

        return postForm("sendDocument", body);
    }

    private Optional<String> resolveTargetChatId() {
        if (!isEnabled()) {
            log.warn("Telegram bot token is empty.");
            return Optional.empty();
        }

        if (isValidChatId(discoveredChatId)) {
            return Optional.of(discoveredChatId);
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

            Optional<String> id = numericChatId(value);
            if (id.isPresent()) return id;
        }

        return Optional.empty();
    }

    private static Optional<String> numericChatId(JsonNode value) {
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() == 0) {
            return Optional.empty();
        }
        return Optional.of(Long.toString(value.longValue()));
    }

    private static boolean isValidChatId(String value) {
        if (value == null || !value.matches("-?\\d+")) return false;
        try { return Long.parseLong(value) != 0; }
        catch (NumberFormatException invalid) { return false; }
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
            String caption) throws IOException {
        String boundary = "----InvestProTelegramBoundary%d".formatted(Instant.now().toEpochMilli());

        byte[] body = buildMultipartBody(
                boundary,
                chatId,
                fileFieldName,
                filePath,
                caption);

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
            String caption) throws IOException {
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
                log.warn("Telegram {} failed HTTP {}", method, response.statusCode());
                return false;
            }

            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            boolean ok = root.path("ok").asBoolean(false);

            if (!ok) {
                log.warn("Telegram {} returned not ok", method);
            }

            return ok;
        } catch (IOException exception) {
            log.warn("Telegram {} IO error", method);
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Telegram {} interrupted", method);
            return false;
        } catch (Exception exception) {
            log.warn("Telegram {} failed ({})", method, exception.getClass().getSimpleName());
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
     * Bot will use ChatGPT to answer questions about market, news, trades,
     * positions, orders, etc.
     *
     * @param apiKey the OpenAI API key
     */
    @SuppressWarnings("unused")
    public void initializeChatGPT(String apiKey) {
        this.openaiApiKey = apiKey == null ? "" : apiKey.trim();
        this.chatgptEnabled = !this.openaiApiKey.isBlank();
        if (chatgptEnabled) {
            log.info("ChatGPT integration initialized for multi-user bot");
        }
        assistantService.configure(openaiApiKey, openaiModel);
    }

    /**
     * Process incoming messages from multiple users via {@code getUpdates}.
     * Supports market info, news, trade queries, positions, orders, risk management,
     * and profitability questions.
     */
    public void configureRemoteAccess(Properties config) {
        allowedUsers.clear();
        allowedChats.clear();
        addIds(allowedUsers, remoteSetting(config, "telegram.allowed_user_ids", "TELEGRAM_ALLOWED_USER_IDS"));
        addIds(allowedChats, remoteSetting(config, "telegram.allowed_chat_ids", "TELEGRAM_ALLOWED_CHAT_IDS"));
        String target = remoteSetting(config, "telegram.chat_id", "TELEGRAM_CHAT_ID");
        preferredChatId = target.trim();
        chatId = null; discoveredChatId = null;
        String model = remoteSetting(config, "telegram.openai_model", "TELEGRAM_OPENAI_MODEL");
        openaiModel = model.isBlank() ? "gpt-4.1-mini" : model;
        if (allowedUsers.isEmpty()) {
            log.warn("Telegram replies disabled: configure TELEGRAM_ALLOWED_USER_IDS with authorized numeric user IDs.");
        }
        assistantService.configure(openaiApiKey, openaiModel);
    }

    public void setOpenaiApiKey(String key) { initializeChatGPT(key); }
    public void setOpenaiModel(String model) {
        openaiModel = model == null || model.isBlank() ? "gpt-4.1-mini" : model.trim();
        assistantService.configure(chatgptEnabled ? openaiApiKey : null, openaiModel);
    }
    public void setChatgptEnabled(boolean enabled) {
        chatgptEnabled = enabled && openaiApiKey != null && !openaiApiKey.isBlank();
        assistantService.configure(chatgptEnabled ? openaiApiKey : null, openaiModel);
    }

    private static String remoteSetting(Properties config, String property, String environment) {
        String value = config.getProperty(property, "").trim();
        if (value.isBlank()) value = config.getProperty(environment, "").trim();
        if (value.isBlank()) value = Objects.toString(System.getenv(environment), "").trim();
        return value;
    }

    private static void addIds(Set<String> target, String input) {
        Arrays.stream(input.split(",")).map(String::trim).filter(value -> value.matches("-?\\d+"))
                .forEach(target::add);
    }

    boolean isAuthorized(String user, String chat, String chatType) {
        return isValidChatId(chat) && "private".equals(chatType) && allowedUsers.contains(user)
                && (allowedChats.isEmpty() || allowedChats.contains(chat));
    }

    public void pollAndProcessUserMessages() {
        if (!isEnabled() || !getUpdatesInFlight.compareAndSet(false, true)) return;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl("getUpdates") + "?offset=" + (lastUpdateId + 1)
                            + "&timeout=10&allowed_updates=" + encode("[\"message\"]")))
                    .timeout(Duration.ofSeconds(15)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) processUpdates(OBJECT_MAPPER.readTree(response.body()));
            else log.warn("Telegram polling HTTP {}", response.statusCode());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (Exception error) { log.warn("Telegram polling failed ({})", error.getClass().getSimpleName()); }
        finally { getUpdatesInFlight.set(false); }
    }

    void processUpdates(JsonNode root) {
        if (!root.path("ok").asBoolean()) return;
        for (JsonNode update : root.path("result")) {
            long id = update.path("update_id").asLong(-1);
            if (id <= lastUpdateId) continue;
            lastUpdateId = id; // Never replay an action after an uncertain broker response.
            JsonNode message = update.path("message");
            String user = message.path("from").path("id").asText("");
            Optional<String> replyChat = numericChatId(message.path("chat").path("id"));
            if (replyChat.isEmpty()) continue;
            String chat = replyChat.get();
            if (isAuthorized(user, chat, message.path("chat").path("type").asText())
                    && !message.path("from").path("is_bot").asBoolean()
                    && (preferredChatId.isBlank() || preferredChatId.equals(chat)
                    || preferredChatId.equals("@" + message.path("chat").path("username").asText()))) {
                if (discoveredChatId == null || !preferredChatId.isBlank()) {
                    discoveredChatId = chat; chatId = chat;
                }
            }
            String text = message.path("text").asText("");
            if (text.isBlank() || message.path("from").path("is_bot").asBoolean()
                    || !isAuthorized(user, chat, message.path("chat").path("type").asText())) continue;
            if (text.length() > 6000) { sendMessageToChat(chat, "Message too long; limit is 6000 characters."); continue; }
            String key = chat + ":" + user;
            UserContext context = userContexts.computeIfAbsent(key, UserContext::new);
            long now = System.currentTimeMillis();
            if (now - context.lastRequestMs < 1500) {
                sendMessageToChat(chat, "Please wait briefly between commands."); continue;
            }
            context.lastRequestMs = now;
            if (!pendingConversations.add(key)) {
                sendMessageToChat(chat, "Your previous request is still being processed.");
                continue;
            }
            UserMessage incoming = new UserMessage(user, message.path("from").path("username").asText("User"),
                    chat, text, message.path("date").asLong());
            try {
                questionWorkers.execute(() -> {
                    try { processUserMessage(context, incoming); }
                    catch (Exception error) {
                        log.warn("Telegram update failed ({})", error.getClass().getSimpleName());
                        sendMessageToChat(chat, "Unable to complete request. Use /orders to verify any pending action.");
                    } finally { pendingConversations.remove(key); }
                });
            } catch (java.util.concurrent.RejectedExecutionException error) {
                pendingConversations.remove(key);
                sendMessageToChat(chat, "Assistant is busy. Please try again shortly.");
            }
        }
    }
    /**
     * Process a single user message and respond accordingly.
     */
    private void processUserMessage(UserContext context, UserMessage message) {
        sendChatAction(message.chatId, ENUM_CHAT_ACTION.typing);
        String key = message.chatId + ":" + message.userId;
        TelegramCommandHandler handler = commandHandler;
        String command = message.text.startsWith("/")
                ? message.text.substring(1).split("\\s+", 2)[0].split("@", 2)[0].toLowerCase(Locale.ROOT) : "";
        String response = "screenshot".equals(command) || "chart".equals(command)
                ? screenshotForChat(message.chatId, message.text)
                : message.text.startsWith("/")
                ? handler == null ? assistantCommand(key, message.text) : handler.handleCommand(message.text, key)
                : askAI(key, message.text);
        if (response != null && !response.isBlank()) sendMessageToChat(message.chatId, response);
        context.lastProcessedUpdate = message.timestamp;
    }

    protected void sendMessageToChat(String targetChatId, String text) {
        if (!isValidChatId(targetChatId) || text == null || text.isBlank()) return;
        for (int start = 0; start < text.length();) {
            int end = Math.min(start + 3800, text.length());
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
            postForm("sendMessage", "chat_id=" + encode(targetChatId) + "&text=" + encode(text.substring(start, end)));
            start = end;
        }
    }

    @FunctionalInterface
    public interface ScreenshotCapture { byte[] capture(boolean chart) throws Exception; }
    private volatile ScreenshotCapture screenshotCapture;

    public void setScreenshotCapture(ScreenshotCapture capture) {
        screenshotCapture = Objects.requireNonNull(capture);
    }

    private String screenshotForChat(String chat, String request) {
        String[] parts = request.trim().split("\\s+", 2);
        String mode = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "chart";
        if (!"chart".equals(mode) && !"app".equals(mode)) return "Usage: /screenshot [chart|app] or /chart";
        ScreenshotCapture capture = screenshotCapture;
        if (capture == null) return "Screenshots are unavailable. Open the InvestPro desktop and a chart.";
        Path file = null;
        try {
            byte[] png = capture.capture("chart".equals(mode));
            file = Files.createTempFile("investpro-telegram-", ".png");
            Files.write(file, png);
            return sendPhotoToChat(chat, file, "InvestPro " + mode + " screenshot")
                    ? "Screenshot sent." : "Screenshot upload failed. Please try again.";
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return "Screenshot interrupted.";
        } catch (Exception error) {
            return "Screenshot unavailable. Open the InvestPro desktop and the chart you want to capture.";
        } finally {
            if (file != null) try { Files.deleteIfExists(file); }
            catch (IOException error) { log.debug("Unable to remove Telegram screenshot temporary file"); }
        }
    }
    public void resetConversation(String user) { assistantService.resetConversation(user); }
    public String askAI(String user, String prompt) { return askAI(user, prompt, null); }
    public String askAI(String user, String prompt, java.util.function.Consumer<String> onDelta) {
        TelegramCommandHandler handler = commandHandler;
        if (prompt.startsWith("/") && handler != null) return handler.handleCommand(prompt, user);
        assistantService.setCommandExecutor(handler == null ? null : (who, command) -> {
            if (commandHandler != handler) return "Selected trading session changed. Request the action again.";
            return handler.handleCommand(command, who);
        });
        return assistantService.askAI(user, questionContext.apply(prompt) + prompt, onDelta);
    }
    private String assistantCommand(String user, String text) {
        String[] parts = text.substring(1).trim().split("\\s+", 2);
        String command = parts[0].split("@", 2)[0].toLowerCase(Locale.ROOT);
        return switch (command) {
            case "start", "help" -> "InvestPro assistant is available even when trading is stopped. Send a question or /ask QUESTION. /chart or /screenshot captures the active desktop chart; /screenshot app captures the app. /reset clears your conversation. Connect an exchange in the desktop app for account commands.";
            case "reset" -> { resetConversation(user); yield "AI conversation cleared."; }
            case "ask", "learn", "invest", "compare", "news" -> parts.length < 2
                    ? "Usage: /" + command + " QUESTION" : askAI(user, parts[1]);
            default -> "Connect an exchange in the desktop app for this command. You can still ask investment questions.";
        };
    }
    /** Called by the application owner, never by trading bot stop. */
    public void close() { stopPolling(); questionWorkers.shutdownNow(); }
    static String responseText(JsonNode response) { return org.investpro.ai.InvestorAssistantService.responseText(response); }

    private void registerCommands() {
        ArrayNode commands = OBJECT_MAPPER.createArrayNode();
        for (String command : List.of("help", "status", "balance", "portfolio", "positions", "orders", "history",
                "quote", "analyze", "watch", "unwatch", "watchlist", "buy", "sell", "limit", "cancel", "confirm",
                "abort", "pause", "resume", "mode", "exchange", "risk", "strategy", "health", "screenshot", "chart",
                "size", "ask", "invest", "compare", "learn", "news", "reset")) {
            commands.addObject().put("command", command).put("description", switch (command) {
                case "buy", "sell", "limit", "cancel", "resume" -> "Preview " + command + " action; confirmation required";
                case "ask" -> "Ask OpenAI a trading or investment question";
                case "help" -> "Show command syntax and examples";
                default -> "InvestPro " + command;
            });
        }
        postForm("setMyCommands", "commands=" + encode(commands.toString()));
    }
    /**
     * Start polling for messages in background thread
     */
    public synchronized void startPolling() {
        if (pollingEnabled) {
            return;
        }

        if (!isEnabled()) {
            log.warn("Cannot start polling: bot token not configured");
            return;
        }

        pollingEnabled = true;
        pollingThread = new Thread(() -> {
            registerCommands();
            log.info("Telegram polling started");
            while (pollingEnabled) {
                try {
                    pollAndProcessUserMessages();
                    //noinspection BusyWait
                    Thread.sleep(2000); // Poll every 2 seconds
                } catch (InterruptedException e) {
                    if (pollingEnabled) {
                        log.debug("Telegram polling interrupted");
                    }
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.warn("Error in Telegram polling loop", e);
                }
            }
            log.info("Telegram polling stopped");
        }, "TelegramPollingThread");

        pollingThread.setDaemon(true);
        pollingThread.start();
    }

    /**
     * Stop polling for messages
     */
    public void stopPolling() {
        if (!pollingEnabled) {
            return;
        }

        pollingEnabled = false;

        if (pollingThread != null && pollingThread.isAlive()) {
            try {
                pollingThread.interrupt();
                pollingThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String rootMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown error";
        }

        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }

        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = current.getClass().getSimpleName();
        }
        return message;
    }

    /**
     * User context for tracking conversation state.
     */
    @Getter
    private static class UserContext {
        private final String userId;
        private long lastProcessedUpdate;
        private long lastRequestMs;

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
