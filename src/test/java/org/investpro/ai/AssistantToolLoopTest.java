package org.investpro.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistantToolLoopTest {
    @Test void newsResultsGoBackToTheModelForASourcedAnswer() throws Exception {
        var client = mock(HttpClient.class);
        var news = response(call("news", "/news SCHW stock 10"));
        var analysis = response(answer("Schwab earnings: Publisher, https://example.com/news"));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(news, analysis);
        var service = new InvestorAssistantService(client); service.configure("test", "gpt-4.1-mini");
        service.setCommandExecutor((user, command) -> "Publisher: Schwab earnings https://example.com/news");
        assertTrue(service.askAI("owner", "Recent Schwab news?").contains("https://example.com/news"));
        var requests = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(2)).send(requests.capture(), any(HttpResponse.BodyHandler.class));
        var body = JSON.readTree(requestBody(requests.getValue()));
        assertTrue(body.path("instructions").asText().contains("/news SCHW stock 10"));
        assertTrue(body.path("instructions").asText().contains("No arbitrary webpage browsing"));
        assertTrue(body.path("input").toString().contains("Publisher: Schwab earnings"));
    }
    private static final ObjectMapper JSON = new ObjectMapper();
    private static String call(String id, String command) {
        var result = JSON.createObjectNode();
        result.putArray("output").addObject().put("type", "function_call").put("name", "investpro_command")
                .put("call_id", id).put("arguments", JSON.createObjectNode().put("command", command).toString());
        return result.toString();
    }
    private static String answer(String text) {
        var result = JSON.createObjectNode();
        result.putArray("output").addObject().put("type", "message").put("role", "assistant")
                .putArray("content").addObject().put("type", "output_text").put("text", text);
        return result.toString();
    }
    @SuppressWarnings("unchecked") private static HttpResponse<String> response(String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200); when(response.body()).thenReturn(body); return response;
    }
    static String requestBody(HttpRequest request) throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        var done = new java.util.concurrent.CompletableFuture<Void>();
        request.bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
            public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(java.nio.ByteBuffer buffer) { byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk); }
            public void onError(Throwable error) { done.completeExceptionally(error); }
            public void onComplete() { done.complete(null); }
        });
        done.get(5, java.util.concurrent.TimeUnit.SECONDS);
        return bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test void dataResultsAreReturnedToTheModelBeforeItSynthesizesAnAnswer() throws Exception {
        var client = mock(HttpClient.class);
        var venues = response(call("venues", "/venues"));
        var quote = response(call("quote", "/data coinbase quote BTC/USD"));
        var analysis = response(answer("The supplied bid/ask imply a 1 USD spread."));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(
                venues, quote, analysis);
        var service = new InvestorAssistantService(client); service.configure("test", "gpt-4.1-mini");
        List<String> commands = new ArrayList<>();
        service.setCommandExecutor((user, command) -> { commands.add(user + command); return command.equals("/venues") ? "coinbase" : "bid=100 ask=101"; });
        assertTrue(service.askAI("telegram-owner", "Assess the spread").contains("1 USD spread"));
        assertEquals(List.of("telegram-owner/venues", "telegram-owner/data coinbase quote BTC/USD"), commands);
        var requests = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(3)).send(requests.capture(), any(HttpResponse.BodyHandler.class));
        var body = JSON.readTree(requestBody(requests.getValue()));
        var outputs = new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        body.path("input").forEach(item -> { if (item.path("type").asText().equals("function_call_output")) outputs.add(item); });
        assertEquals(2, outputs.size());
        assertEquals("quote", outputs.getLast().path("call_id").asText());
        assertEquals("bid=100 ask=101", outputs.getLast().path("output").asText());
        assertFalse(body.path("parallel_tool_calls").asBoolean(true));
    }

    @Test void aTradePreviewAfterAnalysisTerminatesTheLoopWithoutConfirming() throws Exception {
        var client = mock(HttpClient.class);
        var quote = response(call("quote", "/quote BTC/USD"));
        var preview = response(call("trade", "/buy BTC/USD 1"));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(
                quote, preview);
        var service = new InvestorAssistantService(client); service.configure("test", "gpt-4.1-mini");
        List<String> commands = new ArrayList<>();
        service.setCommandExecutor((user, command) -> { commands.add(command); return command.startsWith("/buy") ? "Review action /confirm abc" : "bid=100 ask=101"; });
        assertEquals("Review action /confirm abc", service.askAI("owner", "Buy one BTC after checking the quote"));
        assertEquals(List.of("/quote BTC/USD", "/buy BTC/USD 1"), commands);
        verify(client, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test void repeatedDataCallsAreBoundedAndAnExplicitRequestExecutorDoesNotLeak() throws Exception {
        var client = mock(HttpClient.class);
        var status = response(call("read", "/status"));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(status);
        var service = new InvestorAssistantService(client); service.configure("test", "gpt-4.1-mini");
        service.setCommandExecutor((user, command) -> { fail("Wrong session executor"); return ""; });
        var commands = new ArrayList<String>();
        assertTrue(service.askAI("owner", "Status", null, null, (user, command) -> { commands.add(command); return "status"; }).contains("tool-round limit"));
        assertEquals(6, commands.size());
    }

    @Test void streamedDataToolResultsAreSynthesizedAndStreamedInTheFollowup() throws Exception {
        var client = mock(HttpClient.class);
        @SuppressWarnings("unchecked") HttpResponse<java.io.InputStream> first = mock(HttpResponse.class), second = mock(HttpResponse.class);
        when(first.statusCode()).thenReturn(200); when(second.statusCode()).thenReturn(200);
        when(first.body()).thenReturn(new java.io.ByteArrayInputStream(("data: {\"type\":\"response.completed\",\"response\":" + call("q", "/quote BTC/USD") + "}\n\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        when(second.body()).thenReturn(new java.io.ByteArrayInputStream(("data: {\"type\":\"response.output_text.delta\",\"delta\":\"Spread is 1.\"}\n\n"
                + "data: {\"type\":\"response.completed\",\"response\":" + answer("Spread is 1.") + "}\n\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(first, second);
        var service = new InvestorAssistantService(client); service.configure("test", "gpt-4.1-mini");
        service.setCommandExecutor((user, command) -> "bid=100 ask=101");
        var deltas = new ArrayList<String>();
        assertEquals("Spread is 1.", service.askAI("desktop", "Spread?", deltas::add));
        assertEquals(List.of("Spread is 1."), deltas);
    }
}
