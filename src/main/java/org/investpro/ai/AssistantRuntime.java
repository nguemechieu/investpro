package org.investpro.ai;

import org.investpro.core.SystemCore;
import org.investpro.core.TelegramNotifier;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.providers.EnvironmentCredentialProvider;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Consumer;

/** Application-owned assistant. Trading cores borrow its Telegram transport, never own its lifecycle. */
public final class AssistantRuntime implements AutoCloseable {
    private final TelegramNotifier notifier;
    private final AssistantNewsData news = new AssistantNewsData(new org.investpro.service.RssNewsService());
    private record DeskContext(Exchange exchange, org.investpro.models.trading.TradePair symbol) { }
    private volatile DeskContext deskContext = new DeskContext(null, null);
    private volatile java.util.Map<String, Exchange> venues = java.util.Map.of();
    private volatile java.util.function.Function<String, String> appControl;
    public void setAppControl(java.util.function.Function<String, String> control) { appControl = control; }

    public void updateVenues(java.util.@NonNull Map<String, Exchange> sources) {
        var snapshot = new java.util.LinkedHashMap<String, Exchange>();
        sources.forEach((name, exchange) -> snapshot.put(AssistantAppData.venueId(name), exchange));
        venues = java.util.Collections.unmodifiableMap(snapshot);
    }

    public synchronized void selectExchange(Exchange exchange) {
        if (deskContext.exchange() != exchange) {
            deskContext = new DeskContext(exchange, null);
            notifier.setCommandHandler(null);
        }
    }
    private volatile java.util.function.Supplier<javafx.scene.Scene> screenshotSource = () -> null;
    private volatile java.util.function.Supplier<javafx.scene.Node> chartScreenshotSource = () -> null;
    public void setChartScreenshotSource(java.util.function.Supplier<javafx.scene.Node> source) {
        chartScreenshotSource = java.util.Objects.requireNonNull(source);
    }
    public void setScreenshotSource(java.util.function.Supplier<javafx.scene.Scene> source) { screenshotSource = java.util.Objects.requireNonNull(source); }
    public byte[] captureScreenshot() throws Exception { return AppScreenshot.capture(screenshotSource); }
    public boolean sendScreenshot(byte[] png) throws java.io.IOException {
        var file = java.nio.file.Files.createTempFile("investpro-screenshot-", ".png");
        try { java.nio.file.Files.write(file, png); return notifier.sendPhoto(file, "InvestPro screenshot"); }
        finally { java.nio.file.Files.deleteIfExists(file); }
    }
    private java.util.function.Function<String, String> botControl;
    public synchronized void setBotControl(java.util.function.Function<String, String> control) {
        botControl = control;
        if (notifier.getCommandHandler() != null) notifier.getCommandHandler().setBotControl(control);
    }

    public AssistantRuntime(Properties settings, String telegramToken, String openAiKey) {
        this(new TelegramNotifier(telegramToken));
        notifier.configureRemoteAccess(settings);
        notifier.initializeChatGPT(openAiKey);
    }

    AssistantRuntime(TelegramNotifier transport) {
        notifier = java.util.Objects.requireNonNull(transport);
        notifier.setScreenshotCapture(chart -> chart
                ? AppScreenshot.captureNode(chartScreenshotSource) : captureScreenshot());
        notifier.setQuestionContext(this::contextFor);
        notifier.setAssistantCommandExecutorFactory(this::commandExecutor);
    }

    public static AssistantRuntime fromEnvironment(String token, String key) {
        Properties settings = new Properties();
        try (var input = AssistantRuntime.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) settings.load(input);
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot load assistant settings", error); }
        var credentials = new EnvironmentCredentialProvider();
        for (String name : new String[]{"TELEGRAM_ALLOWED_USER_IDS", "TELEGRAM_ALLOWED_CHAT_IDS", "TELEGRAM_CHAT_ID", "TELEGRAM_OPENAI_MODEL"}) {
            credentials.get(name).ifPresent(value -> settings.setProperty(name, value));
        }
        String resolvedToken = token == null || token.isBlank()
                ? credentials.get("TELEGRAM_BOT_TOKEN").orElse("") : token;
        resolvedToken = AssistantTelegramSettings.apply(settings, resolvedToken,
                java.util.prefs.Preferences.userNodeForPackage(org.investpro.ui.panels.SettingsPanel.class));
        return new AssistantRuntime(settings,
                resolvedToken,
                key == null || key.isBlank() ? credentials.get("OPENAI_API_KEY").orElse("") : key);
    }

    public TelegramNotifier notifier() { return notifier; }
    public boolean isConfigured() { return notifier.getAssistantService().isConfigured(); }
    public AssistantVoice createVoice() { return notifier.getAssistantService().createVoice(); }
    public void start() { if (notifier.isEnabled()) notifier.startPolling(); }
    public synchronized void attach(SystemCore core) {
        deskContext = new DeskContext(core == null ? null : core.getExchange(), null);
        notifier.setCommandHandler(core == null ? null : core.getTelegramCommandHandler());
        if (notifier.getCommandHandler() != null) notifier.getCommandHandler().setBotControl(botControl);
    }
    public synchronized void selectSymbol(org.investpro.models.trading.TradePair symbol) {
        deskContext = new DeskContext(deskContext.exchange(), symbol);
    }
    public String ask(String conversation, String question, Consumer<String> delta) {
        return notifier.askAI(conversation, question, delta);
    }
    public String ask(String conversation, String question, byte[] png, Consumer<String> delta) {
        if (png == null) return ask(conversation, question, delta);
        return notifier.getAssistantService().askAI(conversation, contextFor(question) + question, png, delta);
    }
    public void reset(String conversation) { notifier.resetConversation(conversation); }

    private String contextFor(String question) {
        DeskContext snapshot = deskContext;
        Exchange exchange = snapshot.exchange();
        if (exchange == null) return "Desktop context: no selected connected exchange; live account/market data is unavailable.\n";
        String context = "Desktop exchange: " + exchange.getName() + "; mode: " + exchange.getResolvedTradingMode()
                + "; connected=" + exchange.isConnected() + "; snapshot time: " + Instant.now()
                + ". Bot start/stop does not determine account connectivity.\n";
        var symbol = snapshot.symbol();
        if (symbol != null) context += "Selected symbol: " + symbol.toSlashSymbol() + "; native ID: " + symbol.getNativeSymbol() + ".\n";
        return context + "Available venue IDs: " + venues.keySet()
                + ". Use app data tools for current figures. Action commands operate on the selected desktop exchange only.\n";
    }

    private java.util.function.BiFunction<String, String, String> commandExecutor() {
        var snapshot = deskContext;
        var sources = venues;
        var handler = notifier.getCommandHandler();
        var controls = appControl;
        return (user, command) -> {
            String[] parts = command.substring(1).split("\\s+", 2);
              String name = parts[0].split("@", 2)[0].toLowerCase(Locale.ROOT);
              if (name.equals("news")) return news.execute("/news" + (parts.length > 1 ? " " + parts[1] : ""));
            if (name.equals("venues") || name.equals("data"))
                return AssistantAppData.execute("/" + name + (parts.length > 1 ? " " + parts[1] : ""), sources);
            if (deskContext.exchange() != snapshot.exchange() || notifier.getCommandHandler() != handler)
                return "Selected trading session changed. Request the action again.";
            if (java.util.Set.of("openchart", "charttimeframe", "refreshchart", "resetzoom").contains(name))
                return controls == null ? "Desktop chart controls unavailable." : controls.apply(command);
            if (handler == null) return "Trading controls are unavailable. Connect an exchange; market analysis remains available through /venues and /data.";
            return handler.handleCommand(command, user);
        };
    }

    @Override public void close() { venues = java.util.Map.of(); attach(null); notifier.close(); }
}
