package org.investpro.ai;

import org.junit.jupiter.api.Test;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistantActionsTest {
    @Test void streamedToolCallOnlyRunsAfterCompletedResponseAndCannotConfirm() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<java.io.InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        String stream = "data: {\"type\":\"response.completed\",\"response\":" + toolResponse("/confirm generated") + "}\n\n";
        when(response.body()).thenReturn(new java.io.ByteArrayInputStream(stream.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var service = new InvestorAssistantService(client); service.configure("test", "gpt-4.1-mini");
        java.util.List<String> actions = new java.util.ArrayList<>();
        service.setCommandExecutor((user, command) -> { actions.add(command); return "executed"; });
        assertTrue(service.askAI("owner", "Confirm my order", _ -> {}).contains("confirmations must be entered by you"));
        assertTrue(actions.isEmpty());
    }
    @Test void speechHttpFailureDoesNotAttemptAudioPlayback() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<java.io.InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(403);
        when(response.body()).thenReturn(new java.io.ByteArrayInputStream(new byte[0]));
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(response));
        try (var voice = new AssistantVoice(client, () -> "test")) {
            assertThrows(java.io.IOException.class, () -> voice.speak("Account response"));
        }
        var requests = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).sendAsync(requests.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("/v1/audio/speech", requests.getValue().uri().getPath());
    }
    @Test void modelCanPreviewButCannotConfirmOrRunMultipleActions() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(toolResponse("/buy BIP-20DEC30-CDE 1", "/confirm abc"));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var service = new InvestorAssistantService(client); service.configure("test", "gpt-4.1-mini");
        java.util.List<String> commands = new java.util.ArrayList<>();
        service.setCommandExecutor((user, command) -> { commands.add(user + command); return "Review action /confirm abc"; });
        assertEquals("Review action /confirm abc", service.askAI("owner", "Buy one contract"));
        assertEquals(java.util.List.of("owner/buy BIP-20DEC30-CDE 1"), commands);
        assertFalse(AssistantCommands.isModelCommand("/confirm abc"));
        assertFalse(AssistantCommands.isModelCommand("/buy BTC/USD 1\n/confirm abc"));
        assertFalse(AssistantCommands.isModelCommand("/ask recursive"));
        assertEquals("Review action /confirm abc", service.askAI("owner", "/confirm abc"));
        verify(client, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }
    @Test void transcriptionUsesMultipartAudioEndpointWithoutTradingCore() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"text\":\"Pause my bot\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        try (var voice = new AssistantVoice(client, () -> "test")) {
            assertEquals("Pause my bot", voice.transcribe(new byte[]{1, 2, 3}));
        }
        var requests = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(requests.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("/v1/audio/transcriptions", requests.getValue().uri().getPath());
        assertTrue(requests.getValue().headers().firstValue("Content-Type").orElseThrow().startsWith("multipart/form-data; boundary="));
    }
    private static String toolResponse(String... commands) {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var response = json.createObjectNode(); var output = response.putArray("output");
        for (String command : commands) output.addObject().put("type", "function_call").put("name", "investpro_command")
                .put("arguments", json.createObjectNode().put("command", command).toString());
        return response.toString();
    }
}
