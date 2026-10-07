package org.investpro.exchange;

import org.investpro.exchange.oanda.Oanda;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.lang.reflect.InvocationTargetException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OandaMutationRetryTest {
    private Oanda adapter(HttpClient client) throws Exception {
        Oanda adapter = mock(Oanda.class, CALLS_REAL_METHODS);
        var field = Oanda.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        field.set(adapter, client);
        return adapter;
    }

    private Object send(Oanda adapter, HttpRequest request) throws Exception {
        var method = Oanda.class.getDeclaredMethod("sendWithExponentialBackoffSync",
                HttpRequest.class, int.class, long.class, long.class);
        method.setAccessible(true);
        return method.invoke(adapter, request, 3, 1L, 1L);
    }

    @Test void lostOrderResponseIsNotResubmitted() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("response lost"));
        Oanda adapter = adapter(client);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://example.invalid/orders"))
                .POST(HttpRequest.BodyPublishers.ofString("{}" )).build();
        var exception = assertThrows(InvocationTargetException.class, () -> send(adapter, request));
        assertInstanceOf(IOException.class, exception.getCause());
        verify(client, times(1)).send(any(), any());
    }

    @Test void gatewayErrorDoesNotRepeatCloseMutation() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(503);
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        Oanda adapter = adapter(client);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://example.invalid/positions/close"))
                .PUT(HttpRequest.BodyPublishers.ofString("{}" )).build();
        assertSame(response, send(adapter, request));
        verify(client, times(1)).send(any(), any());
    }
}
