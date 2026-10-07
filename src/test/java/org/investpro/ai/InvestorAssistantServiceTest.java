package org.investpro.ai;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvestorAssistantServiceTest {
    private static final String STREAM = """
            event: response.output_text.delta
            data: {"type":"response.output_text.delta","delta":"Diversify "}

            data: {"type":"response.output_text.delta","delta":"exposure."}

            data: {"type":"response.completed","response":{"output":[{"type":"message","content":[{"type":"output_text","text":"Diversify exposure."}]}]}}

            """;

    @Test void consumesDeltasAndRequiresCompletion() throws Exception {
        var deltas = new ArrayList<String>();
        assertEquals("Diversify exposure.", InvestorAssistantService.readStream(new BufferedReader(new StringReader(STREAM)), deltas::add));
        assertEquals(java.util.List.of("Diversify ", "exposure."), deltas);
        assertThrows(IOException.class, () -> InvestorAssistantService.readStream(new BufferedReader(new StringReader(
                "data: {\"type\":\"response.output_text.delta\",\"delta\":\"Partial\"}\n\n")), _ -> {}));
        assertThrows(IOException.class, () -> InvestorAssistantService.readStream(new BufferedReader(new StringReader(
                "data: {\"type\":\"response.failed\"}\n\n")), _ -> {}));
    }

    @Test @SuppressWarnings("unchecked") void streamingUsesResponsesAndSavesCompletedConversation() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenAnswer(_ -> new ByteArrayInputStream(STREAM.getBytes(StandardCharsets.UTF_8)));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var service = new InvestorAssistantService(client);
        service.configure("test-key", "gpt-4.1-mini");
        var deltas = new ArrayList<String>();
        assertEquals("Diversify exposure.", service.askAI("desktop", "Risk?", deltas::add));
        assertEquals("Diversify exposure.", service.askAI("desktop", "Fees?", _ -> {}));
        var requests = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(2)).send(requests.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("https://api.openai.com/v1/responses", requests.getValue().uri().toString());
        assertEquals(java.util.List.of("Diversify ", "exposure."), deltas);
    }

    @Test void worksWithoutTradingCoreAndReportsMissingConfiguration() {
        var service = new InvestorAssistantService(mock(HttpClient.class));
        assertTrue(service.askAI("desktop", "Explain bonds", _ -> {}).contains("not configured"));
    }
}
