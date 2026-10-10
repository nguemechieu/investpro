package org.investpro.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.ai.InvestorAssistantService;
import org.investpro.utils.ENUM_CHAT_ACTION;
import org.jspecify.annotations.NonNull;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Telegram notifier for InvestPro.
 * <p>
 * Features:
 * <ul>
 *   <li>Sends text, photos and documents</li>
 *   <li>Autodetects the notification chat from authorized {@code getUpdates} traffic</li>
 *   <li>Multiuser assistant bot (OpenAI) with per-user throttling and a bounded worker pool</li>
 *   <li>Routes slash commands to the trading command handler</li>
 * </ul>
 * <p>
 * Security model:
 * <ul>
 *   <li>Only private chats from users listed in {@code TELEGRAM_ALLOWED_USER_IDS} are served.</li>
 *   <li>Every reply goes to the chat the message came from.</li>
 *   <li>The notification target is either the configured {@code TELEGRAM_CHAT_ID} (if it is an
 *       allowed user's private chat) or the first authorized chat seen in an update.</li>
 *   <li>The AI assistant can never issue {@code /confirm}; the human must type it.</li>
 *   <li>The bot token is redacted from log output.</li>
 * </ul>
 * <p>
 * Auto-detection only works after the bot receives an update: open the bot in Telegram
 * and press Start (or send any message).
 */
@Getter
@Setter
@Slf4j
public class TelegramNotifier {
    private static final String TELEGRAM_API_BASE = "https://api.telegram.org/bot";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String NO_CHAT_ID_LOG = "Telegram {} skipped: no chat_id configured.";
    private static final String DEFAULT_MODEL = "gpt-4.1-mini";

    private static final int MAX_INCOMING_LENGTH = 6000;
    private static final int MESSAGE_CHUNK = 3800;           // Telegram hard limit is 4096
    private static final int CAPTION_LIMIT = 1024;
    private static final long PHOTO_LIMIT_BYTES = 10L * 1024 * 1024;
    private static final long DOCUMENT_LIMIT_BYTES = 50L * 1024 * 1024;
    private static final long MIN_REQUEST_GAP_MS = 1500L;
    private static final long DETECTION_MIN_INTERVAL_MS = 30_000L;
    private static final long INITIAL_BACKOFF_MS = 1_000L;
    private static final long MAX_BACKOFF_MS = 60_000L;
    private static final int LONG_POLL_SECONDS = 10;
    private static final long MAX_RETRY_AFTER_SECONDS = 10L;
    private static final AtomicInteger WORKER_IDS = new AtomicInteger();

    private final String botToken;
    private final HttpClient httpClient;
    private final InvestorAssistantService assistantService;

    // Notification target. Only ever set from authorized updates (or validated configuration).
    private volatile String preferredChatId = "";
    private volatile String discoveredChatId;
    private volatile long lastUpdateId = -1L;

    // Multi-user support
    private final Map<String, UserContext> userContexts = new ConcurrentHashMap<>();
    private final Set<String> allowedUsers = ConcurrentHashMap.newKeySet();
    private final Set<String> allowedChats = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingConversations = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor questionWorkers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), runnable -> {
        Thread thread = new Thread(runnable, "TelegramQuestion-" + WORKER_IDS.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    private volatile Function<String, String> questionContext = ignored -> "";
    private volatile Supplier<BiFunction<String, String, String>> assistantCommandExecutorFactory;
    private volatile String openaiModel = DEFAULT_MODEL;
    private volatile String openaiApiKey = "";
    private volatile boolean chatgptEnabled = false;
    private volatile BiConsumer<String, String> orderCommentHandler;
    private volatile TelegramCommandHandler commandHandler;
    private volatile ScreenshotCapture screenshotCapture;

    private volatile boolean pollingEnabled = false;
    private volatile Thread pollingThread;

    // Guard against concurrent getUpdates calls (Telegram HTTP 409)
    private final AtomicBoolean getUpdatesInFlight = new AtomicBoolean(false);
    private volatile long lastDetectionAttemptMs = 0L;
    private volatile boolean lastDetectionFailed = false;

    public TelegramNotifier(String botToken) {
        this(botToken, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build());
    }

    TelegramNotifier(String botToken, HttpClient httpClient) {
        this.botToken = safe(botToken);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.assistantService = new InvestorAssistantService(httpClient);
    }

    // ------------------------------------------------------------------------------------
    // Accessors (explicit on purpose: no setters for security-relevant state, no secrets)
    // ------------------------------------------------------------------------------------

    public boolean isEnabled() {
        return !botToken.isBlank();
    }

    public boolean hasTargetChat() {
        return currentTarget().isPresent();
    }



    public String getChatId() {
        return currentTarget().orElse(null);
    }

    public Set<String> getAllowedUsers() {
        return Set.copyOf(allowedUsers);
    }


    public void setQuestionContext(Function<String, String> questionContext) {
        this.questionContext = questionContext == null ? ignored -> "" : questionContext;
    }

    // ------------------------------------------------------------------------------------
    // Chat detection
    // ------------------------------------------------------------------------------------

    /**
     * Resolve the notification chat, running a detection pass if none is known yet.
     *
     * @return the chat ID, or empty if none is available
     */
    public Optional<String> detectAndUseLatestChatId() {
        if (allowedUsers.isEmpty()) return Optional.empty();
        Optional<String> known = currentTarget();
        if (known.isPresent()) return known;
        detectChatIds();
        // Only processUpdates may select an authorized chat observed in an API update.
        return currentTarget();
    }

    /**
     * Detect all chat IDs visible to this bot from {@code getUpdates}.
     * Updates are dispatched (not just inspected) before they are acknowledged.
     *
     * @return set of detected chat IDs
     */
    public Set<String> detectChatIds() {
        Set<String> chatIds = new LinkedHashSet<>();

        if (!isEnabled()) {
            log.warn("Telegram bot token is empty. Cannot detect chat IDs.");
            return chatIds;
        }

        long now = System.currentTimeMillis();
        if (now - lastDetectionAttemptMs < DETECTION_MIN_INTERVAL_MS) {
            log.debug("Telegram getUpdates skipped: cooldown active.");
            return chatIds;
        }
        lastDetectionAttemptMs = now;

        PollResult result = fetchAndProcess(0, chatIds);
        lastDetectionFailed = result == PollResult.FAILED;
        if (lastDetectionFailed) {
            log.warn("Telegram chat detection failed. Configure TELEGRAM_CHAT_ID or check network/DNS.");
        }
        return chatIds;
    }

    private Optional<String> currentTarget() {
        String discovered = discoveredChatId;
        if (isValidChatId(discovered)) return Optional.of(discovered);
        // In a private chat the chat id equals the user id, so a configured numeric
        // target that belongs to an allowed user can be trusted without waiting for an update.
        String preferred = preferredChatId;
        if (isValidChatId(preferred) && allowedUsers.contains(preferred)) return Optional.of(preferred);
        return Optional.empty();
    }

    private Optional<String> resolveTargetChatId() {
        if (!isEnabled()) {
            log.warn("Telegram bot token is empty.");
            return Optional.empty();
        }
        return detectAndUseLatestChatId();
    }

    private Optional<String> extractChatId(JsonNode update) {
        if (update == null || update.isMissingNode()) {
            return Optional.empty();
        }

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
            Optional<String> id = numericChatId(update.at(path));
            if (id.isPresent()) return id;
        }
        return Optional.empty();
    }

    private static Optional<String> numericChatId(JsonNode value) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() == 0) {
            return Optional.empty();
        }
        return Optional.of(Long.toString(value.longValue()));
    }

    private static boolean isValidChatId(String value) {
        if (value == null || !value.matches("-?\\d+")) return false;
        try {
            return Long.parseLong(value) != 0;
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    // ------------------------------------------------------------------------------------
    // Sending
    // ------------------------------------------------------------------------------------

    /** Send plain text to the notification chat. */
    public boolean send(String message) {
        return sendMessage(message);
    }

    /** Send plain text (no parse mode, so no escaping problems) to the notification chat. */
    public boolean sendMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        Optional<String> target = resolveTargetChatId();
        if (target.isEmpty()) {
            log.debug(NO_CHAT_ID_LOG, "message");
            return false;
        }
        return deliverPlain(target.get(), message);
    }

    /** Send Markdown; if Telegram rejects the markup, the text is re-sent as plain text. */
    public boolean sendMarkdown(String markdownMessage) {
        return sendFormatted(markdownMessage, "Markdown", "markdown message");
    }

    /** Send HTML; if Telegram rejects the markup, the text is re-sent as plain text. */
    @SuppressWarnings("unused")
    public boolean sendHtml(String htmlMessage) {
        return sendFormatted(htmlMessage, "HTML", "HTML message");
    }

    private boolean sendFormatted(String text, String parseMode, String label) {
        if (text == null || text.isBlank()) {
            return false;
        }
        Optional<String> target = resolveTargetChatId();
        if (target.isEmpty()) {
            log.debug(NO_CHAT_ID_LOG, label);
            return false;
        }
        String chat = target.get();
        if (text.length() > MESSAGE_CHUNK) {
            // Splitting would cut markup in half, so long messages go out as plain text.
            return deliverPlain(chat, text);
        }
        ApiResult result = postFormResult("sendMessage",
                form("chat_id", chat, "text", text, "parse_mode", parseMode));
        if (result.ok()) return true;
        if (result.status() == 400) {
            log.debug("Telegram rejected {} markup; retrying as plain text.", parseMode);
            return postForm("sendMessage", form("chat_id", chat, "text", text));
        }
        return false;
    }

    /**
     * Show a typing/upload indicator while a request is processed.
     */
    public void sendChatAction(String targetChatId, ENUM_CHAT_ACTION action) {
        if (!isValidChatId(targetChatId) || action == null) {
            return;
        }
        try {
            postForm("sendChatAction",
                    form("chat_id", targetChatId, "action", action.toString().toLowerCase(Locale.ROOT)));
        } catch (Exception e) {
            log.debug("Error sending chat action: {}", describe(e));
        }
    }

    /** Send a photo by local path. */
    @SuppressWarnings("unused")
    public boolean sendPhoto(Path photoPath, String caption) {
        if (photoPath == null || !Files.isRegularFile(photoPath)) {
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

    /** Photos above Telegram's 10 MB photo limit are sent as documents instead. */
    protected boolean sendPhotoToChat(String targetChatId, Path photoPath, String caption) {
        try {
            if (photoPath != null && Files.isRegularFile(photoPath) && Files.size(photoPath) > PHOTO_LIMIT_BYTES) {
                return sendFileToChat("sendDocument", "document", targetChatId, photoPath, caption,
                        DOCUMENT_LIMIT_BYTES);
            }
        } catch (IOException ignored) {
            // fall through to the normal path, which reports the failure
        }
        return sendFileToChat("sendPhoto", "photo", targetChatId, photoPath, caption, PHOTO_LIMIT_BYTES);
    }

    /** Send a photo by public URL or Telegram file_id. */
    @SuppressWarnings("unused")
    public boolean sendPhoto(String photoUrlOrFileId, String caption) {
        return sendByReference("sendPhoto", "photo", photoUrlOrFileId, caption);
    }

    /** Send a document by local path. */
    @SuppressWarnings("unused")
    public boolean sendDocument(Path documentPath, String caption) {
        if (documentPath == null || !Files.isRegularFile(documentPath)) {
            log.warn("Telegram document skipped because file does not exist: {}", documentPath);
            return false;
        }
        Optional<String> target = resolveTargetChatId();
        if (target.isEmpty()) {
            log.warn(NO_CHAT_ID_LOG, "document");
            return false;
        }
        return sendFileToChat("sendDocument", "document", target.get(), documentPath, caption,
                DOCUMENT_LIMIT_BYTES);
    }

    /** Send a document by public URL or Telegram file_id. */
    @SuppressWarnings("unused")
    public boolean sendDocument(String documentUrlOrFileId, String caption) {
        return sendByReference("sendDocument", "document", documentUrlOrFileId, caption);
    }

    private boolean sendByReference(String method, String field, String reference, String caption) {
        if (reference == null || reference.isBlank()) {
            return false;
        }
        Optional<String> target = resolveTargetChatId();
        if (target.isEmpty()) {
            log.warn(NO_CHAT_ID_LOG, field);
            return false;
        }
        String cleanCaption = truncate(safe(caption), CAPTION_LIMIT);
        String body = form("chat_id", target.get(), field, reference.trim())
                + (cleanCaption.isBlank() ? "" : "&" + form("caption", cleanCaption));
        return postForm(method, body);
    }

    private boolean sendFileToChat(String method, String field, String targetChatId, Path path,
                                   String caption, long maxBytes) {
        if (!isValidChatId(targetChatId)) return false;
        try {
            if (path == null || !Files.isRegularFile(path)) {
                log.warn("Telegram {} skipped: file not found: {}", method, path);
                return false;
            }
            long size = Files.size(path);
            if (size > maxBytes) {
                log.warn("Telegram {} skipped: {} is {} bytes (limit {}).", method, path.getFileName(), size, maxBytes);
                return false;
            }
            return postMultipart(method, targetChatId, field, path, caption);
        } catch (Exception exception) {
            log.warn("Telegram {} failed: {}", method, describe(exception));
            return false;
        }
    }

    protected void sendMessageToChat(String targetChatId, String text) {
        deliverPlain(targetChatId, text);
    }

    /** Sends plain text in chunks, preferring line breaks; stops at the first failed chunk. */
    private boolean deliverPlain(String targetChatId, String text) {
        if (!isValidChatId(targetChatId) || text == null || text.isBlank()) return false;
        boolean sentAny = false;
        for (String part : splitMessage(text, MESSAGE_CHUNK)) {
            if (part.isBlank()) continue;
            if (!postForm("sendMessage", form("chat_id", targetChatId, "text", part))) return false;
            sentAny = true;
        }
        return sentAny;
    }

    static List<String> splitMessage(String text, int limit) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + limit, text.length());
            if (end < text.length()) {
                int newline = text.lastIndexOf('\n', end - 1);
                if (newline > start + limit / 2) {
                    end = newline + 1;
                } else if (Character.isHighSurrogate(text.charAt(end - 1))) {
                    end--;
                }
            }
            parts.add(text.substring(start, end));
            start = end;
        }
        return parts;
    }

    // ------------------------------------------------------------------------------------
    // HTTP plumbing
    // ------------------------------------------------------------------------------------

    private record ApiResult(boolean ok, int status, JsonNode root) {
        static final ApiResult FAILED = new ApiResult(false, -1, MissingNode.getInstance());
    }

    private boolean postForm(String method, String body) {
        return postFormResult(method, body).ok();
    }

    private ApiResult postFormResult(String method, String body) {
        if (!isEnabled()) {
            return ApiResult.FAILED;
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl(method)))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return execute(request, method);
    }

    private boolean postMultipart(String method, String targetChatId, String fileFieldName,
                                  Path filePath, String caption) throws IOException {
        String boundary = "----InvestProTelegramBoundary" + UUID.randomUUID().toString().replace("-", "");
        List<byte[]> body = buildMultipartBody(boundary, targetChatId, fileFieldName, filePath, caption);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl(method)))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(body))
                .build();
        return execute(request, method).ok();
    }

    private List<byte[]> buildMultipartBody(String boundary, String targetChatId, String fileFieldName,
                                            Path filePath, String caption) throws IOException {
        String fileName = filePath.getFileName().toString().replaceAll("[\"\\r\\n]", "");
        String cleanCaption = truncate(safe(caption), CAPTION_LIMIT);

        StringBuilder prefix = new StringBuilder();
        appendFormField(prefix, boundary, "chat_id", targetChatId);
        if (!cleanCaption.isBlank()) {
            appendFormField(prefix, boundary, "caption", cleanCaption);
        }
        prefix.append("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"").append(fileFieldName)
                .append("\"; filename=\"").append(fileName).append("\"\r\n")
                .append("Content-Type: ").append(detectContentType(filePath)).append("\r\n\r\n");

        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        return List.of(prefix.toString().getBytes(StandardCharsets.UTF_8), Files.readAllBytes(filePath), suffix);
    }

    private static void appendFormField(StringBuilder builder, String boundary, String name, String value) {
        builder.append("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n")
                .append(value == null ? "" : value).append("\r\n");
    }

    /**
     * Executes a Telegram request. Retries once on HTTP 429 (honouring retry_after, capped).
     * Never logs URLs or bodies, so the bot token cannot leak through this path.
     */
    private ApiResult execute(HttpRequest request, String method) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode root = parseQuietly(response.body());
                int status = response.statusCode();

                if (status < 400 && root.path("ok").asBoolean(false)) {
                    return new ApiResult(true, status, root);
                }

                if (status == 429 && attempt == 0) {
                    long wait = Math.max(1L, Math.min(
                            root.path("parameters").path("retry_after").asLong(1L), MAX_RETRY_AFTER_SECONDS));
                    log.warn("Telegram {} rate limited; retrying in {}s", method, wait);
                    Thread.sleep(wait * 1000L);
                    continue;
                }

                if (status == 409) {
                    log.warn("Telegram {} conflict (HTTP 409): another instance or a webhook is using this bot.", method);
                } else {
                    log.warn("Telegram {} failed HTTP {}: {}", method, status, root.path("description").asText(""));
                }
                return new ApiResult(false, status, root);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.warn("Telegram {} interrupted", method);
                return ApiResult.FAILED;
            } catch (Exception exception) {
                log.warn("Telegram {} failed: {}", method, describe(exception));
                return ApiResult.FAILED;
            }
        }
        return ApiResult.FAILED;
    }

    private static JsonNode parseQuietly(String body) {
        try {
            return body == null || body.isBlank() ? MissingNode.getInstance() : OBJECT_MAPPER.readTree(body);
        } catch (IOException invalid) {
            return MissingNode.getInstance();
        }
    }

    private String apiUrl(String method) {
        return TELEGRAM_API_BASE + botToken + "/" + method;
    }

    private static String form(String... keyValues) {
        StringJoiner joiner = new StringJoiner("&");
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            joiner.add(encode(keyValues[i]) + "=" + encode(keyValues[i + 1]));
        }
        return joiner.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String detectContentType(Path path) {
        try {
            String type = Files.probeContentType(path);
            if (type != null && !type.isBlank()) {
                return type;
            }
        } catch (IOException ignored) {
            // fall back to the extension below
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String extension = dot >= 0 ? name.substring(dot + 1) : "";
        return switch (extension) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "pdf" -> "application/pdf";
            case "csv" -> "text/csv";
            case "txt" -> "text/plain";
            case "json" -> "application/json";
            default -> "application/octet-stream";
        };
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        int end = max;
        if (Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    /** Redacts the bot token from any text that might be logged. */
    private String redact(String text) {
        if (text == null) return "";
        return botToken.isBlank() ? text : text.replace(botToken, "<token>");
    }

    /** Safe description of an exception for logs: class + redacted root message, never the stack trace. */
    private String describe(Throwable throwable) {
        if (throwable == null) return "unknown error";
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) message = current.getClass().getSimpleName();
        return redact(message) + " (" + throwable.getClass().getSimpleName() + ")";
    }

    // ------------------------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------------------------

    /**
     * Initialize OpenAI integration for assistant replies.
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

    public void setOpenaiApiKey(String key) {
        initializeChatGPT(key);
    }

    public void setOpenaiModel(String model) {
        openaiModel = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
        assistantService.configure(chatgptEnabled ? openaiApiKey : null, openaiModel);
    }

    /** Loads the allow-lists, notification target and model from properties or environment. */
    public void configureRemoteAccess(Properties config) {
        allowedUsers.clear();
        allowedChats.clear();
        addIds(allowedUsers, remoteSetting(config, "telegram.allowed_user_ids", "TELEGRAM_ALLOWED_USER_IDS"));
        addIds(allowedChats, remoteSetting(config, "telegram.allowed_chat_ids", "TELEGRAM_ALLOWED_CHAT_IDS"));
        preferredChatId = remoteSetting(config, "telegram.chat_id", "TELEGRAM_CHAT_ID").trim();
        discoveredChatId = null;
        String model = remoteSetting(config, "telegram.openai_model", "TELEGRAM_OPENAI_MODEL");
        openaiModel = model.isBlank() ? DEFAULT_MODEL : model;
        if (allowedUsers.isEmpty()) {
            log.warn("Telegram replies disabled: configure TELEGRAM_ALLOWED_USER_IDS with authorized numeric user IDs.");
        }
        assistantService.configure(openaiApiKey, openaiModel);
    }

    private static String remoteSetting(Properties config, String property, String environment) {
        String value = config == null ? "" : config.getProperty(property, "").trim();
        if (value.isBlank() && config != null) value = config.getProperty(environment, "").trim();
        if (value.isBlank()) value = Objects.toString(System.getenv(environment), "").trim();
        return value;
    }

    private static void addIds(Set<String> target, String input) {
        Arrays.stream(input.split(","))
                .map(String::trim)
                .filter(value -> value.matches("-?\\d+"))
                .forEach(target::add);
    }

    boolean isAuthorized(String user, String chat, String chatType) {
        return isValidChatId(chat) && "private".equals(chatType) && allowedUsers.contains(user)
                && (allowedChats.isEmpty() || allowedChats.contains(chat));
    }

    // ------------------------------------------------------------------------------------
    // Incoming updates
    // ------------------------------------------------------------------------------------

    private enum PollResult { OK, SKIPPED, FAILED }

    /** One polling round. Public for compatibility; the polling thread uses {@link #pollOnce()}. */
    public void pollAndProcessUserMessages() {
        pollOnce();
    }

    private PollResult pollOnce() {
        return fetchAndProcess(LONG_POLL_SECONDS, null);
    }

    /**
     * The single place that calls getUpdates (shared by detection and polling).
     * Only one call may be in flight at a time, otherwise Telegram answers HTTP 409.
     */
    private PollResult fetchAndProcess(int longPollSeconds, Set<String> seenChats) {
        if (!isEnabled()) return PollResult.FAILED;
        if (!getUpdatesInFlight.compareAndSet(false, true)) {
            log.debug("Telegram getUpdates skipped: another thread already polling.");
            return PollResult.SKIPPED;
        }
        try {
            StringBuilder query = new StringBuilder("?timeout=").append(longPollSeconds)
                    .append("&allowed_updates=").append(encode("[\"message\"]"));
            if (lastUpdateId >= 0) {
                query.append("&offset=").append(lastUpdateId + 1);
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl("getUpdates") + query))
                    .timeout(Duration.ofSeconds(longPollSeconds + 15L))
                    .GET()
                    .build();

            ApiResult result = execute(request, "getUpdates");
            if (!result.ok()) return PollResult.FAILED;

            JsonNode root = result.root();
            if (seenChats != null) {
                for (JsonNode update : root.path("result")) {
                    extractChatId(update).ifPresent(seenChats::add);
                }
            }
            // Dispatch before the next getUpdates acknowledges these updates.
            processUpdates(root);
            return PollResult.OK;
        } catch (Exception exception) {
            log.warn("Telegram polling failed: {}", describe(exception));
            return PollResult.FAILED;
        } finally {
            getUpdatesInFlight.set(false);
        }
    }

    void processUpdates(@NonNull JsonNode root) {
        if (!root.path("ok").asBoolean(false)) return;
        for (JsonNode update : root.path("result")) {
            long id = update.path("update_id").asLong(-1);
            if (id <= lastUpdateId) continue;
            lastUpdateId = id; // Never replay an action after an uncertain broker response.
            try {
                handleMessage(update.path("message"));
            } catch (Exception exception) {
                log.warn("Telegram update {} failed: {}", id, describe(exception));
            }
        }
    }

    private void handleMessage(JsonNode message) {
        JsonNode from = message.path("from");
        String user = from.path("id").asText("");
        Optional<String> replyChat = numericChatId(message.path("chat").path("id"));
        if (replyChat.isEmpty() || from.path("is_bot").asBoolean(false)) return;

        String chat = replyChat.get();
        String chatType = message.path("chat").path("type").asText("");
        if (!isAuthorized(user, chat, chatType)) return; // unauthorized senders are ignored silently

        adoptNotificationTarget(chat, message.path("chat").path("username").asText(""));

        String text = message.path("text").asText("");
        if (text.isBlank()) return;
        if (text.length() > MAX_INCOMING_LENGTH) {
            sendMessageToChat(chat, "Message too long; limit is " + MAX_INCOMING_LENGTH + " characters.");
            return;
        }

        String key = chat + ":" + user;
        UserContext context = userContexts.computeIfAbsent(key, UserContext::new);
        long now = System.currentTimeMillis();
        if (now - context.lastRequestMs < MIN_REQUEST_GAP_MS) {
            sendMessageToChat(chat, "Please wait briefly between commands.");
            return;
        }
        context.lastRequestMs = now;

        if (!pendingConversations.add(key)) {
            sendMessageToChat(chat, "Your previous request is still being processed.");
            return;
        }

        UserMessage incoming = new UserMessage(user, from.path("username").asText("User"),
                chat, text, message.path("date").asLong());
        try {
            questionWorkers.execute(() -> {
                try {
                    processUserMessage(context, incoming);
                } catch (Exception error) {
                    log.warn("Telegram update failed: {}", describe(error));
                    sendMessageToChat(chat, "Unable to complete request. Use /orders to verify any pending action.");
                } finally {
                    pendingConversations.remove(key);
                }
            });
        } catch (RejectedExecutionException error) {
            pendingConversations.remove(key);
            sendMessageToChat(chat, "Assistant is busy. Please try again shortly.");
        }
    }

    /** Called only for authorized, non-bot, private-chat messages. */
    private void adoptNotificationTarget(String chat, String username) {
        String preferred = preferredChatId;
        boolean matchesPreferred = preferred.isBlank()
                || preferred.equals(chat)
                || (!username.isBlank() && preferred.equals("@" + username));
        if (!matchesPreferred) return;
        if (discoveredChatId == null || !preferred.isBlank()) {
            discoveredChatId = chat;
        }
    }

    private void processUserMessage(UserContext context, UserMessage message) {
        sendChatAction(message.chatId(), ENUM_CHAT_ACTION.typing);
        String key = message.chatId() + ":" + message.userId();
        String response = route(key, message);
        if (response != null && !response.isBlank()) sendMessageToChat(message.chatId(), response);
        context.lastProcessedUpdate = message.timestamp();
    }

    private String route(String key, UserMessage message) {
        String text = message.text();
        String command = commandName(text);
        if ("screenshot".equals(command) || "chart".equals(command)) {
            return screenshotForChat(message.chatId(), text);
        }
        if (!text.startsWith("/")) {
            return askAI(key, text);
        }
        if (assistantCommandExecutorFactory != null) {
            return askAI(key, text);
        }
        TelegramCommandHandler handler = commandHandler;
        return handler == null ? assistantCommand(key, text) : handler.handleCommand(text, key);
    }

    /** First word of a command, without the leading slash or an {@code @botname} suffix. */
    private static String firstWord(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
        return trimmed.split("\\s+", 2)[0].split("@", 2)[0].toLowerCase(Locale.ROOT);
    }

    private static String commandName(String text) {
        return text != null && text.startsWith("/") ? firstWord(text) : "";
    }

    // ------------------------------------------------------------------------------------
    // Screenshots
    // ------------------------------------------------------------------------------------

    @FunctionalInterface
    public interface ScreenshotCapture {
        byte[] capture(boolean chart) throws Exception;
    }

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
            if (png == null || png.length == 0) {
                return "Screenshot unavailable. Open the InvestPro desktop and the chart you want to capture.";
            }
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
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException error) {
                    log.debug("Unable to remove Telegram screenshot temporary file");
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------
    // Assistant
    // ------------------------------------------------------------------------------------

    public void resetConversation(String user) {
        assistantService.resetConversation(user);
    }

    public String askAI(String user, String prompt) {
        return askAI(user, prompt, null);
    }

    public String askAI(String user, String prompt, Consumer<String> onDelta) {
        if (prompt == null || prompt.isBlank()) return "";

        TelegramCommandHandler handler = commandHandler;
        Supplier<BiFunction<String, String, String>> factory = assistantCommandExecutorFactory;
        BiFunction<String, String, String> executor = factory != null
                ? factory.get()
                : handler == null ? null : (who, command) -> {
            if (commandHandler != handler) return "Selected trading session changed. Request the action again.";
            return handler.handleCommand(command, who);
        };

        // A slash command typed by the user: run it directly (this is the human path).
        if (prompt.startsWith("/") && executor != null) return executor.apply(user, prompt);

        String context = Objects.toString(questionContext.apply(prompt), "");
        return assistantService.askAI(user, context + prompt, null, onDelta, guardModelExecutor(executor));
    }

    /**
     * Commands the language model triggers must never include the final confirmation
     * of a trade: only the human typing /confirm may approve an action.
     */
    private static BiFunction<String, String, String> guardModelExecutor(
            BiFunction<String, String, String> delegate) {
        if (delegate == null) return null;
        return (who, command) -> "confirm".equals(firstWord(command))
                ? "The assistant can't confirm trades. Send /confirm yourself to approve the action."
                : delegate.apply(who, command);
    }

    private String assistantCommand(String user, String text) {
        String[] parts = text.substring(1).trim().split("\\s+", 2);
        String command = parts[0].split("@", 2)[0].toLowerCase(Locale.ROOT);
        return switch (command) {
            case "start", "help" -> "InvestPro assistant is available even when trading is stopped. "
                    + "Send a question or /ask QUESTION. /chart or /screenshot captures the active desktop chart; "
                    + "/screenshot app captures the app. /reset clears your conversation. "
                    + "Connect an exchange in the desktop app for account commands.";
            case "reset" -> {
                resetConversation(user);
                yield "AI conversation cleared.";
            }
            case "ask", "learn", "invest", "compare", "news" -> parts.length < 2
                    ? "Usage: /" + command + " QUESTION" : askAI(user, parts[1]);
            default -> "Connect an exchange in the desktop app for this command. You can still ask investment questions.";
        };
    }

    static String responseText(JsonNode response) {
        return InvestorAssistantService.responseText(response);
    }

    // ------------------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------------------

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
        postForm("setMyCommands", form("commands", commands.toString()));
    }

    /** Start polling for messages in a background thread. */
    public synchronized void startPolling() {
        if (pollingEnabled) {
            return;
        }
        if (!isEnabled()) {
            log.warn("Cannot start polling: bot token not configured");
            return;
        }

        pollingEnabled = true;
        Thread thread = new Thread(this::pollLoop, "TelegramPollingThread");
        thread.setDaemon(true);
        pollingThread = thread;
        thread.start();
    }

    private void pollLoop() {
        registerCommands();
        log.info("Telegram polling started");
        long backoffMs = INITIAL_BACKOFF_MS;
        try {
            while (pollingEnabled) {
                PollResult result;
                try {
                    result = pollOnce();
                } catch (Exception e) {
                    log.warn("Error in Telegram polling loop: {}", describe(e));
                    result = PollResult.FAILED;
                }
                if (result == PollResult.OK) {
                    backoffMs = INITIAL_BACKOFF_MS; // the long poll itself paces the loop
                } else if (result == PollResult.SKIPPED) {
                    Thread.sleep(500L);
                } else {
                    Thread.sleep(backoffMs);
                    backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
                }
            }
        } catch (InterruptedException e) {
            if (pollingEnabled) {
                log.debug("Telegram polling interrupted");
            }
            Thread.currentThread().interrupt();
        } finally {
            // Allow startPolling() to work again if the loop died on its own.
            if (pollingThread == Thread.currentThread()) {
                pollingEnabled = false;
            }
            log.info("Telegram polling stopped");
        }
    }

    /** Stop polling for messages. */
    public synchronized void stopPolling() {
        if (!pollingEnabled) {
            return;
        }
        pollingEnabled = false;

        Thread thread = pollingThread;
        if (thread != null && thread.isAlive() && thread != Thread.currentThread()) {
            thread.interrupt();
            try {
                thread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        pollingThread = null;
    }

    /** Called by the application owner, never by trading bot stop. */
    public void close() {
        stopPolling();
        questionWorkers.shutdownNow();
    }

    // ------------------------------------------------------------------------------------
    // Value types
    // ------------------------------------------------------------------------------------

    /** Per-user conversation state. */
    private static class UserContext {
        final String userId;
        volatile long lastProcessedUpdate;
        volatile long lastRequestMs;

        UserContext(String userId) {
            this.userId = userId;
            this.lastProcessedUpdate = System.currentTimeMillis();
        }
    }

    private record UserMessage(String userId, String userName, String chatId, String text, long timestamp) {
    }
}