package org.investpro.ai;

import org.junit.jupiter.api.Test;
import javax.sound.sampled.*;
import java.io.*;
import java.net.http.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistantVoicePlaybackTest {
    @SuppressWarnings("unchecked")
    private static void stream(HttpClient client, InputStream audio) {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200); when(response.body()).thenReturn(audio);
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(response));
    }

    @Test void speechPlaysBeforeTheStreamFinishesAndPreservesSplitSamples() throws Exception {
        var client = mock(HttpClient.class); var line = mock(SourceDataLine.class);
        var written = new ByteArrayOutputStream();
        when(line.write(any(byte[].class), anyInt(), anyInt())).thenAnswer(call -> {
            byte[] bytes = call.getArgument(0); int offset = call.getArgument(1), count = call.getArgument(2);
            assertEquals(0, count % 2); written.write(bytes, offset, count); return count;
        });
        var audio = new ByteArrayInputStream(new byte[]{1, 2, 3, 4}) {
            int reads;
            @Override public synchronized int read(byte[] bytes, int offset, int length) {
                if (++reads == 3) assertArrayEquals(new byte[]{1, 2}, written.toByteArray());
                return super.read(bytes, offset, Math.min(length, 1));
            }
        };
        stream(client, audio);
        var started = new java.util.concurrent.atomic.AtomicInteger();
        try (var voice = new AssistantVoice(client, () -> "test", format -> line)) {
            voice.speak("Actual reply text", started::incrementAndGet);
        }
        assertArrayEquals(new byte[]{1, 2, 3, 4}, written.toByteArray()); assertEquals(1, started.get());
        var format = org.mockito.ArgumentCaptor.forClass(AudioFormat.class);
        verify(line).open(format.capture()); verify(line).start(); verify(line).drain(); verify(line).close();
        assertEquals(24000f, format.getValue().getSampleRate()); assertEquals(16, format.getValue().getSampleSizeInBits());
        assertFalse(format.getValue().isBigEndian()); assertEquals(1, format.getValue().getChannels());
    }

    @Test void stopCancelsPendingGenerationAndUnblocksTheSpeechWorker() throws Exception {
        var client = mock(HttpClient.class); var line = mock(SourceDataLine.class);
        var pending = new CompletableFuture<HttpResponse<InputStream>>(); var requested = new CountDownLatch(1);
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            requested.countDown(); return pending;
        });
        try (var voice = new AssistantVoice(client, () -> "test", format -> line);
             var worker = Executors.newSingleThreadExecutor()) {
            var task = worker.submit(() -> { voice.speak("Reply"); return null; });
            assertTrue(requested.await(2, TimeUnit.SECONDS)); voice.stopSpeaking(); task.get(2, TimeUnit.SECONDS);
            assertTrue(pending.isCancelled());
        }
        verifyNoInteractions(line);
    }

    @Test void stopDuringPlaybackClosesTheStreamAndDoesNotDrainOldAudio() throws Exception {
        var client = mock(HttpClient.class); var line = mock(SourceDataLine.class);
        var audio = spy(new ByteArrayInputStream(new byte[]{1, 2, 3, 4})); stream(client, audio);
        try (var voice = new AssistantVoice(client, () -> "test", format -> line)) {
            when(line.write(any(byte[].class), anyInt(), anyInt())).thenAnswer(call -> {
                voice.stopSpeaking(); return 2;
            });
            voice.speak("Reply");
        }
        verify(audio, atLeastOnce()).close(); verify(line, never()).drain();
    }

    @Test void emptyOrTruncatedAudioReportsFailure() throws Exception {
        for (byte[] pcm : new byte[][]{new byte[0], new byte[]{1}}) {
            var client = mock(HttpClient.class); var line = mock(SourceDataLine.class);
            stream(client, new ByteArrayInputStream(pcm));
            try (var voice = new AssistantVoice(client, () -> "test", format -> line)) {
                assertThrows(IOException.class, () -> voice.speak("Reply"));
            }
            verifyNoInteractions(line);
        }
    }
}
