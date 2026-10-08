package org.investpro.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.contracts.OrderExecutionProvider;
import org.investpro.models.trading.TradePair;
import org.investpro.models.trading.Ticker;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TelegramRemoteDeskTest {
    @Test
    void repliesUseIncoming64BitChatIdInsteadOfConfiguredTargetOrSenderId() throws Exception {
        var bot = spy(new TelegramNotifier(""));
        Properties config = new Properties();
        config.setProperty("TELEGRAM_ALLOWED_USER_IDS", "123,456");
        config.setProperty("TELEGRAM_CHAT_ID", "999");
        bot.configureRemoteAccess(config);
        doNothing().when(bot).sendChatAction(anyString(), any());
        doNothing().when(bot).sendMessageToChat(anyString(), anyString());
        doReturn("First answer").when(bot).askAI("4500000000001:123", "First question");
        doReturn("Second answer").when(bot).askAI("4500000000002:456", "Second question");
        try {
            bot.processUpdates(new ObjectMapper().readTree("""
                    {"ok":true,"result":[
                    {"update_id":1,"message":{"from":{"id":123},"chat":{"id":4500000000001,"type":"private"},"text":"First question"}},
                    {"update_id":2,"message":{"from":{"id":456},"chat":{"id":4500000000002,"type":"private"},"text":"Second question"}}]}
                    """));
            verify(bot, timeout(5000)).sendMessageToChat("4500000000001", "First answer");
            verify(bot, timeout(5000)).sendMessageToChat("4500000000002", "Second answer");
            verify(bot, never()).sendMessageToChat(eq("999"), anyString());
            verify(bot, never()).sendMessageToChat(eq("123"), anyString());
        } finally { bot.close(); }
    }

    @Test
    void malformedChatIdsCannotDiscoverATargetOrDispatchQuestions() throws Exception {
        var bot = spy(new TelegramNotifier(""));
        Properties config = new Properties();
        config.setProperty("TELEGRAM_ALLOWED_USER_IDS", "123");
        bot.configureRemoteAccess(config);
        try {
            bot.processUpdates(new ObjectMapper().readTree("""
                    {"ok":true,"result":[
                    {"update_id":1,"message":{"from":{"id":123},"chat":{"id":0,"type":"private"},"text":"Question"}},
                    {"update_id":2,"message":{"from":{"id":123},"chat":{"id":"@user","type":"private"},"text":"Question"}},
                    {"update_id":3,"message":{"from":{"id":123},"chat":{"id":1.5,"type":"private"},"text":"Question"}},
                    {"update_id":4,"message":{"from":{"id":123},"chat":{"id":9223372036854775808,"type":"private"},"text":"Question"}},
                    {"update_id":5,"message":{"from":{"id":123},"chat":{"username":"user","type":"private"},"text":"Question"}}]}
                    """));
            assertNull(bot.getChatId());
            assertFalse(bot.hasTargetChat());
            verify(bot, never()).askAI(anyString(), anyString());
            verify(bot, never()).sendMessageToChat(anyString(), anyString());
        } finally { bot.close(); }
    }

    @Test
    void invalidReplyDestinationsNeverReachTelegramTransport() throws Exception {
        var client = mock(java.net.http.HttpClient.class);
        var bot = new TelegramNotifier("test-token", client);
        try {
            for (String chat : java.util.List.of("", "0", "@username", "9223372036854775808")) {
                bot.sendMessageToChat(chat, "Answer");
                assertFalse(bot.sendPhotoToChat(chat, java.nio.file.Path.of("missing.png"), "Chart"));
            }
            verifyNoInteractions(client);
        } finally { bot.close(); }
    }
    @Test void notificationChatIsDiscoveredFromAuthorizedGetUpdatesInsteadOfGuessed() throws Exception {
        var client = mock(java.net.http.HttpClient.class);
        java.net.http.HttpResponse<String> response = mock(java.net.http.HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"ok":true,"result":[{"update_id":1,"message":{"from":{"id":123},"chat":{"id":123,"type":"private"}}}]}
                """);
        when(client.send(any(java.net.http.HttpRequest.class), any(java.net.http.HttpResponse.BodyHandler.class))).thenReturn(response);
        var bot = new TelegramNotifier("test-token", client);
        Properties settings = new Properties(); settings.setProperty("TELEGRAM_ALLOWED_USER_IDS", "123");
        bot.configureRemoteAccess(settings);
        assertNull(bot.getChatId());
        assertEquals(java.util.Optional.of("123"), bot.detectAndUseLatestChatId());
        assertEquals("123", bot.getChatId());
        var request = org.mockito.ArgumentCaptor.forClass(java.net.http.HttpRequest.class);
        verify(client).send(request.capture(), any(java.net.http.HttpResponse.BodyHandler.class));
        assertEquals("api.telegram.org", request.getValue().uri().getHost());
        assertTrue(request.getValue().uri().getPath().endsWith("/getUpdates"));
        bot.close();
    }
    @Test void botStartNeedsConfirmationAndStopUsesDesktopWorkflow() throws Exception {
        var core = mock(SystemCore.class); var exchange = mock(Exchange.class);
        when(core.getExchange()).thenReturn(exchange); when(exchange.isPaperTrading()).thenReturn(true);
        when(exchange.getResolvedTradingMode()).thenReturn("PAPER");
        var commands = new TelegramTradingCommands(core);
        java.util.List<String> actions = new java.util.ArrayList<>();
        commands.setBotControl(action -> { actions.add(action); return "requested " + action; });
        String preview = commands.handle("u", "botstart", new String[]{"botstart"});
        assertTrue(actions.isEmpty());
        String code = preview.split("/confirm ")[1].split("\\s")[0];
        assertEquals("requested start", commands.handle("u", "confirm", new String[]{"confirm", code}));
        assertEquals("requested stop", commands.handle("u", "botstop", new String[]{"botstop"}));
        assertEquals(java.util.List.of("start", "stop"), actions);
    }
    @Test void invalidBracketPricesDoNotCreatePreview() throws Exception {
        var core = mock(SystemCore.class); var exchange = mock(Exchange.class);
        when(core.getExchange()).thenReturn(exchange); when(exchange.isPaperTrading()).thenReturn(true);
        var commands = new TelegramTradingCommands(core);
        assertTrue(commands.handle("u", "bracket", new String[]{"bracket", "buy", "BTC/USD", "1", "100", "110", "90"})
                .contains("stop on the loss side"));
        verify(exchange, never()).orderExecution();
    }
    @Test void nativePerpetualStopOrdersRequireConfirmation() throws Exception {
        SystemCore core = mock(SystemCore.class); Exchange exchange = mock(Exchange.class);
        OrderExecutionProvider execution = mock(OrderExecutionProvider.class);
        when(core.getExchange()).thenReturn(exchange); when(exchange.isPaperTrading()).thenReturn(true);
        when(exchange.getResolvedTradingMode()).thenReturn("PAPER"); when(exchange.orderExecution()).thenReturn(execution);
        when(execution.createStopOrder(any(), eq(Side.SELL), eq(1.0), eq(100.0))).thenReturn(CompletableFuture.completedFuture("stop-1"));
        var commands = new TelegramTradingCommands(core);
        String preview = commands.handle("desktop", "stop", new String[]{"stop", "sell", "BIP-20DEC30-CDE", "1", "100"});
        assertTrue(preview.contains("BIP-20DEC30-CDE")); verifyNoInteractions(execution);
        String code = preview.split("/confirm ")[1].split("\\s")[0];
        assertTrue(commands.handle("desktop", "confirm", new String[]{"confirm", code}).contains("stop-1"));
        var pair = org.mockito.ArgumentCaptor.forClass(TradePair.class);
        verify(execution).createStopOrder(pair.capture(), eq(Side.SELL), eq(1.0), eq(100.0));
        assertTrue(pair.getValue().isPerpetual());
    }
    @Test void slowQuestionDoesNotBlockPollingOrAnotherUserAndAskWorksWithoutCore() throws Exception {
        var bot = spy(new TelegramNotifier(""));
        Properties config = new Properties(); config.setProperty("telegram.allowed_user_ids", "123,456");
        bot.configureRemoteAccess(config);
        doNothing().when(bot).sendChatAction(anyString(), any());
        doNothing().when(bot).sendMessageToChat(anyString(), anyString());
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        doAnswer(_ -> { entered.countDown(); release.await(5, java.util.concurrent.TimeUnit.SECONDS); return "First answer"; })
                .when(bot).askAI("123:123", "Slow question");
        doReturn("Bonds explained").when(bot).askAI("456:456", "Explain bonds");
        var updates = new ObjectMapper().readTree("""
                {"ok":true,"result":[
                {"update_id":1,"message":{"text":"Slow question","from":{"id":123},"chat":{"id":123,"type":"private"}}},
                {"update_id":2,"message":{"text":"/ask Explain bonds","from":{"id":456},"chat":{"id":456,"type":"private"}}}]}
                """);
        try {
            assertTimeout(java.time.Duration.ofSeconds(1), () -> bot.processUpdates(updates));
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
            verify(bot, timeout(3000)).sendMessageToChat("456", "Bonds explained");
            assertEquals(2, bot.getLastUpdateId());
            verify(bot, never()).sendMessageToChat("123", "First answer");
        } finally { release.countDown(); bot.close(); }
    }

    @Test
    void uppercaseSettingsWorkWhenLowercaseSettingsAreBlank() {
        TelegramNotifier bot = new TelegramNotifier("");
        Properties config = new Properties();
        config.setProperty("telegram.allowed_user_ids", " ");
        config.setProperty("TELEGRAM_ALLOWED_USER_IDS", "123");
        config.setProperty("TELEGRAM_ALLOWED_CHAT_IDS", "123");
        config.setProperty("TELEGRAM_CHAT_ID", "123");
        bot.configureRemoteAccess(config);
        assertTrue(bot.isAuthorized("123", "123", "private"));
        assertFalse(bot.isAuthorized("456", "123", "private"));
        assertNull(bot.getChatId()); // Configuration selects a preference; the API must verify the chat.
    }

    @Test
    @SuppressWarnings("unchecked")
    void discoveryAnswersQuestionsInsteadOfDiscardingThemAndDoesNotReplay() throws Exception {
        var client = mock(java.net.http.HttpClient.class);
        java.net.http.HttpResponse<String> response = mock(java.net.http.HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        String updates = """
                {"ok":true,"result":[{"update_id":10,"message":{"text":"What is diversification?",
                "from":{"id":123},"chat":{"id":123,"type":"private"},"date":1}}]}
                """;
        when(response.body()).thenReturn(updates);
        when(client.send(any(java.net.http.HttpRequest.class), any(java.net.http.HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        var bot = spy(new TelegramNotifier("test-token", client));
        Properties config = new Properties();
        config.setProperty("telegram.allowed_user_ids", "123");
        bot.configureRemoteAccess(config);
        doNothing().when(bot).sendChatAction(anyString(), any());
        doNothing().when(bot).sendMessageToChat(anyString(), anyString());
        doReturn("Diversification spreads exposure.").when(bot).askAI("123:123", "What is diversification?");
        assertTrue(bot.detectChatIds().contains("123"));
        bot.processUpdates(new ObjectMapper().readTree(updates));
        verify(bot, timeout(5000).times(1)).askAI("123:123", "What is diversification?");
        verify(bot, timeout(5000).times(1)).sendMessageToChat("123", "Diversification spreads exposure.");
        assertEquals(10, bot.getLastUpdateId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void aiUsesResponsesApiAndKeepsConversationsSeparate() throws Exception {
        var client = mock(java.net.http.HttpClient.class);
        java.net.http.HttpResponse<String> response = mock(java.net.http.HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"Answer\"}]}]}");
        when(client.send(any(java.net.http.HttpRequest.class), any(java.net.http.HttpResponse.BodyHandler.class))).thenReturn(response);
        var bot = new TelegramNotifier("", client);
        bot.initializeChatGPT("test-key");
        assertEquals("Answer", bot.askAI("first", "Diversification?"));
        assertEquals("Answer", bot.askAI("first", "And fees?"));
        assertEquals("Answer", bot.askAI("second", "Bonds?"));
        bot.resetConversation("first");
        assertEquals("Answer", bot.askAI("first", "New conversation"));
        var requests = org.mockito.ArgumentCaptor.forClass(java.net.http.HttpRequest.class);
        verify(client, times(4)).send(requests.capture(), any(java.net.http.HttpResponse.BodyHandler.class));
        int[] expectedTurns = {1, 3, 1, 1};
        for (int i = 0; i < 4; i++) {
            var request = requests.getAllValues().get(i);
            assertEquals("https://api.openai.com/v1/responses", request.uri().toString());
            var bytes = new java.io.ByteArrayOutputStream();
            var complete = new CompletableFuture<Void>();
            request.bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
                public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
                public void onNext(java.nio.ByteBuffer buffer) { byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk); }
                public void onError(Throwable error) { complete.completeExceptionally(error); }
                public void onComplete() { complete.complete(null); }
            });
            complete.get(5, java.util.concurrent.TimeUnit.SECONDS);
            var body = new ObjectMapper().readTree(bytes.toByteArray());
            assertFalse(body.path("store").asBoolean());
            assertEquals(expectedTurns[i], body.path("input").size());
            assertFalse(body.has("tools"));
        }
    }

    @Test
    void accessRequiresAllowlistedUserAndPrivateChat() {
        TelegramNotifier bot = new TelegramNotifier("");
        Properties config = new Properties();
        config.setProperty("telegram.allowed_user_ids", "123, 456");
        config.setProperty("telegram.allowed_chat_ids", "123");
        bot.configureRemoteAccess(config);
        assertTrue(bot.isAuthorized("123", "123", "private"));
        assertFalse(bot.isAuthorized("999", "123", "private"));
        assertFalse(bot.isAuthorized("123", "123", "group"));
        assertFalse(bot.isAuthorized("456", "456", "private"));
        bot.configureRemoteAccess(new Properties());
        assertFalse(bot.isAuthorized("123", "123", "private"));
    }

    @Test
    void processesEntireBatchAndDoesNotReplayUpdates() throws Exception {
        TelegramNotifier bot = spy(new TelegramNotifier(""));
        Properties config = new Properties();
        config.setProperty("telegram.allowed_user_ids", "123,456");
        bot.configureRemoteAccess(config);
        doNothing().when(bot).sendChatAction(anyString(), any());
        doNothing().when(bot).sendMessageToChat(anyString(), anyString());
        TelegramCommandHandler handler = mock(TelegramCommandHandler.class);
        when(handler.handleCommand(anyString(), anyString())).thenReturn("OK");
        bot.setCommandHandler(handler);
        var updates = new ObjectMapper().readTree("""
                {"ok":true,"result":[
                {"update_id":1,"message":{"text":"/help","from":{"id":123},"chat":{"id":123,"type":"private"}}},
                {"update_id":2,"message":{"text":"/health","from":{"id":456},"chat":{"id":456,"type":"private"}}},
                {"update_id":3,"message":{"text":"/balance","from":{"id":999},"chat":{"id":999,"type":"private"}}}]}
                """);
        bot.processUpdates(updates);
        bot.processUpdates(updates);
        verify(handler, timeout(5000)).handleCommand("/help", "123:123");
        verify(handler, timeout(5000)).handleCommand("/health", "456:456");
        verifyNoMoreInteractions(handler);
        assertEquals(3, bot.getLastUpdateId());
    }

    @Test
    void confirmationIsBoundToUserConsumedOnceAndUsesPaperProvider() throws Exception {
        SystemCore core = mock(SystemCore.class);
        Exchange exchange = mock(Exchange.class);
        OrderExecutionProvider paper = mock(OrderExecutionProvider.class);
        when(core.getExchange()).thenReturn(exchange);
        when(exchange.isPaperTrading()).thenReturn(true);
        when(exchange.getResolvedTradingMode()).thenReturn("PAPER");
        when(exchange.orderExecution()).thenReturn(paper);
        Ticker ticker = mock(Ticker.class);
        when(ticker.getAskPrice()).thenReturn(100.0);
        when(exchange.fetchTicker(any())).thenReturn(CompletableFuture.completedFuture(ticker));
        when(paper.createMarketOrder(any(), eq(Side.BUY), eq(2.0)))
                .thenReturn(CompletableFuture.completedFuture("paper-1"));
        var commands = new TelegramTradingCommands(core);
        String preview = commands.handle("owner", "buy", new String[]{"buy", "BTC/USD", "2"});
        String code = preview.split("/confirm ")[1].split("\\s")[0];
        verifyNoInteractions(paper);
        assertEquals("No matching pending action.", commands.handle("other", "confirm", new String[]{"confirm", code}));
        assertTrue(commands.handle("owner", "confirm", new String[]{"confirm", code}).contains("paper-1"));
        assertEquals("No matching pending action.", commands.handle("owner", "confirm", new String[]{"confirm", code}));
        verify(paper).createMarketOrder(any(TradePair.class), eq(Side.BUY), eq(2.0));
        verify(exchange, never()).createMarketOrder(any(), any(), anyDouble());
    }

    @Test
    void positionSizingCalculatesRiskBudgetAndRejectsZeroStopDistance() throws Exception {
        SystemCore core = mock(SystemCore.class);
        var commands = new TelegramTradingCommands(core);
        String result = commands.handle("u", "size", new String[]{"size","10000","1","100","95"});
        assertTrue(result.contains("Risk budget: 100.0000"));
        assertTrue(result.contains("Quantity: 20.00000000"));
        assertTrue(commands.handle("u", "size", new String[]{"size","10000","1","100","100"})
                .contains("entry and stop must differ"));
    }

    @Test
    void disconnectedLiveExchangeCannotCreatePreview() throws Exception {
        SystemCore core = mock(SystemCore.class);
        Exchange exchange = mock(Exchange.class);
        when(core.getExchange()).thenReturn(exchange);
        when(exchange.getResolvedTradingMode()).thenReturn("LIVE");
        var commands = new TelegramTradingCommands(core);
        assertTrue(commands.handle("u", "buy", new String[]{"buy","BTC/USD","1"}).contains("not authenticated"));
        verify(exchange, never()).orderExecution();
    }

    @Test
    void modeChangesInvalidatePendingOrdersAndInvalidQuantitiesAreRejected() throws Exception {
        SystemCore core = mock(SystemCore.class);
        Exchange exchange = mock(Exchange.class);
        when(core.getExchange()).thenReturn(exchange);
        when(exchange.isPaperTrading()).thenReturn(true);
        when(exchange.getResolvedTradingMode()).thenReturn("PAPER");
        var commands = new TelegramTradingCommands(core);
        assertThrows(IllegalArgumentException.class, () -> commands.handle("u", "buy", new String[]{"buy","BTC/USD","NaN"}));
        String preview = commands.handle("u", "limit", new String[]{"limit","buy","BTC/USD","1","100"});
        String code = preview.split("/confirm ")[1].split("\\s")[0];
        when(exchange.getResolvedTradingMode()).thenReturn("LIVE");
        assertTrue(commands.handle("u", "confirm", new String[]{"confirm",code}).contains("mode changed"));
        verify(exchange, never()).orderExecution();
    }

    @Test
    void extractsResponsesTextWithoutConfusingReasoningWithAnswer() throws Exception {
        var result = new ObjectMapper().readTree("""
                {"output":[{"type":"reasoning","summary":[]},{"type":"message","content":[
                {"type":"output_text","text":"Diversify."},{"type":"output_text","text":"Review fees."}]}]}
                """);
        assertEquals("Diversify.\nReview fees.", TelegramNotifier.responseText(result));
    }
}
