package org.investpro.ai;

import org.junit.jupiter.api.Test;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistantScreenshotTest {
    @Test void screenshotIsSentAsImageInputWithoutActionTools() throws Exception {
        HttpClient client = mock(HttpClient.class); HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"Chart visible\"}]}]}");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var service = new InvestorAssistantService(client); service.configure("test", "gpt-4.1-mini");
        service.setCommandExecutor((user, command) -> { fail("Screenshot analysis cannot execute commands"); return ""; });
        assertEquals("Chart visible", service.askAI("desktop", "Explain chart", new byte[]{1, 2, 3}, null));
        var requests = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(requests.capture(), any(HttpResponse.BodyHandler.class));
        var bytes = new java.io.ByteArrayOutputStream();
        var complete = new java.util.concurrent.CompletableFuture<Void>();
        requests.getValue().bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
            public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(java.nio.ByteBuffer buffer) { byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk); }
            public void onError(Throwable error) { complete.completeExceptionally(error); }
            public void onComplete() { complete.complete(null); }
        });
        complete.get(5, java.util.concurrent.TimeUnit.SECONDS);
        var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(bytes.toByteArray());
        var content = body.path("input").get(0).path("content");
        assertEquals("input_text", content.get(0).path("type").asText());
        assertEquals("data:image/png;base64,AQID", content.get(1).path("image_url").asText());
        assertFalse(body.has("tools"));
    }
    @Test void telegramScreenshotFileIsRemovedAfterFailedUpload() throws Exception {
        var notifier = mock(org.investpro.core.TelegramNotifier.class);
        java.util.List<java.nio.file.Path> files = new java.util.ArrayList<>();
        when(notifier.sendPhoto(any(java.nio.file.Path.class), anyString())).thenAnswer(call -> {
            java.nio.file.Path path = call.getArgument(0); files.add(path);
            assertArrayEquals(new byte[]{1, 2, 3}, java.nio.file.Files.readAllBytes(path)); return false;
        });
        try (var runtime = new AssistantRuntime(notifier)) {
            assertFalse(runtime.sendScreenshot(new byte[]{1, 2, 3}));
        }
        assertEquals(1, files.size()); assertFalse(java.nio.file.Files.exists(files.getFirst()));
    }
    @Test void emptyScreenshotDoesNotMakeAnApiRequest() {
        var client = mock(HttpClient.class); var service = new InvestorAssistantService(client);
        service.configure("test", "gpt-4.1-mini");
        assertTrue(service.askAI("desktop", "Explain", new byte[0], null).contains("Screenshot must"));
        verifyNoInteractions(client);
    }
}
