package org.investpro.ai;

import org.investpro.core.SystemCore;
import org.investpro.core.TelegramNotifier;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.providers.EnvironmentCredentialProvider;
import java.time.Instant;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Application-owned assistant. Trading cores borrow its Telegram transport, never own its lifecycle. */
public final class AssistantRuntime implements AutoCloseable {
    private final TelegramNotifier notifier;
    private record DeskContext(Exchange exchange, org.investpro.models.trading.TradePair symbol) { }
    private volatile DeskContext deskContext = new DeskContext(null, null);
    private volatile java.util.function.Supplier<javafx.scene.Scene> screenshotSource = () -> null;
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
        notifier.setQuestionContext(this::contextFor);
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
        return new AssistantRuntime(settings,
                token == null || token.isBlank() ? credentials.get("TELEGRAM_BOT_TOKEN").orElse("") : token,
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
        String lower = question.toLowerCase(Locale.ROOT);
        if (lower.matches("(?s).*(account|balance|equity|portfolio|position|profit|margin).*")) {
            try {
                var account = exchange.tradingAccount().get(5, TimeUnit.SECONDS);
                if (account != null) context += "Account snapshot: total balance=" + account.getTotalBalance()
                        + ", equity=" + account.getEquity() + ", used margin=" + account.getMarginUsed()
                        + ", available margin=" + account.getMarginAvailable() + ". Amounts use the account's currency; currency and individual holdings are not supplied.\n";
                else context += "Account snapshot unavailable.\n";
            } catch (InterruptedException error) { Thread.currentThread().interrupt(); context += "Account lookup interrupted.\n"; }
            catch (Exception error) { context += "Account snapshot unavailable; do not assume balances or positions.\n"; }
        }
        var symbol = snapshot.symbol();
        if (symbol != null && lower.matches("(?s).*(market|price|profit|return|strategy|risk|" + java.util.regex.Pattern.quote(symbol.toSlashSymbol().toLowerCase(Locale.ROOT)) + ").*")) {
            try {
                var ticker = exchange.fetchTicker(symbol).get(5, TimeUnit.SECONDS);
                if (ticker != null) context += "Selected market snapshot: " + symbol.toSlashSymbol()
                        + "; bid=" + ticker.getBidPrice() + ", ask=" + ticker.getAskPrice() + ". Historical returns, fees and news are not supplied.\n";
            } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            catch (Exception error) { context += "Selected market quote unavailable.\n"; }
        }
        return context;
    }

    @Override public void close() { attach(null); notifier.close(); }
}
