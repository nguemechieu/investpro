package org.investpro.ai;

import org.junit.jupiter.api.Test;
import javax.sound.sampled.*;
import java.io.*;
import java.net.http.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistantVoicePlaybackTest {
    @Test void configuredGainIsClampedToTheDevicesRange() throws Exception {
        for (float requested : new float[]{4, -50, 50}) {
            var client = mock(HttpClient.class); var line = mock(SourceDataLine.class);
            FloatControl gain = new FloatControl(FloatControl.Type.MASTER_GAIN, -12, 6, 1, 0, 0, "dB") {};
            when(line.isControlSupported(FloatControl.Type.MASTER_GAIN)).thenReturn(true);
            when(line.getControl(FloatControl.Type.MASTER_GAIN)).thenReturn(gain);
            when(line.write(any(byte[].class), anyInt(), anyInt())).thenAnswer(call -> call.getArgument(2));
            stream(client, new ByteArrayInputStream(new byte[]{1, 2}));
            try (var voice = new AssistantVoice(client, () -> "test", _ -> line)) {
                voice.setPlaybackGainDb(requested); voice.speak("Read all of this.");
            }
            assertEquals(Math.clamp(requested, -12, 6), gain.getValue());
        }
    }

    @Test void failingOneChunkStopsTheSequenceAndSurfacesTheError() {
        var client = mock(HttpClient.class); var line = mock(SourceDataLine.class);
        @SuppressWarnings("unchecked") HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(429);
        var audio = spy(new ByteArrayInputStream(new byte[0])); when(response.body()).thenReturn(audio);
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(response));
        try (var voice = new AssistantVoice(client, () -> "test", _ -> line)) {
            var error = assertThrows(IOException.class, () -> voice.speak("Long reply. ".repeat(500)));
            assertTrue(error.getMessage().contains("429"));
        }
        verify(client, times(1)).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verifyNoInteractions(line);
    }

    @Test void aNewGenerationCancelsTheOldResponseAndItsRemainingChunks() throws Exception {
        var client = mock(HttpClient.class); var line = mock(SourceDataLine.class);
        var pending = new CompletableFuture<HttpResponse<InputStream>>();
        var requested = new CountDownLatch(1);
        @SuppressWarnings("unchecked") HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2}));
        when(line.write(any(byte[].class), anyInt(), anyInt())).thenAnswer(call -> call.getArgument(2));
        var requests = new java.util.concurrent.atomic.AtomicInteger();
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(_ -> {
            if (requests.incrementAndGet() == 1) { requested.countDown(); return pending; }
            return CompletableFuture.completedFuture(response);
        });
        try (var voice = new AssistantVoice(client, () -> "test", _ -> line);
             var worker = Executors.newSingleThreadExecutor()) {
            var old = worker.submit(() -> { voice.speak("Old response. ".repeat(500)); return null; });
            assertTrue(requested.await(2, TimeUnit.SECONDS));
            voice.speak("New complete response."); old.get(2, TimeUnit.SECONDS);
            assertTrue(pending.isCancelled()); assertEquals(2, requests.get());
        }
    }

    @Test void longRepliesAreSplitWithoutDroppingAnyTextOrBreakingUnicode() {
        String text = "Market analysis. Price is $0.00123. 📈\n".repeat(250) + "Final recommendation.";
        var chunks = AssistantVoice.speechChunks(text);
        assertTrue(chunks.size() > 1);
        assertEquals(text, String.join("", chunks));
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 1500);
            assertFalse(Character.isHighSurrogate(chunk.charAt(chunk.length() - 1)));
            assertFalse(Character.isLowSurrogate(chunk.charAt(0)));
        }
        String noSpaces = "x".repeat(1499) + "📈" + "x".repeat(5000);
        assertEquals(noSpaces, String.join("", AssistantVoice.speechChunks(noSpaces)));
        assertFalse(Character.isHighSurrogate(AssistantVoice.speechChunks(noSpaces).getFirst().charAt(1498)));
    }

    @Test void everyChunkIsSubmittedInOrderAndDrainedBeforeTheNextRequest() throws Exception {
        var client = mock(HttpClient.class);
        var inputs = new java.util.ArrayList<String>();
        var line = mock(SourceDataLine.class);
        var drains = new java.util.concurrent.atomic.AtomicInteger();
        when(line.write(any(byte[].class), anyInt(), anyInt())).thenAnswer(call -> call.getArgument(2));
        doAnswer(_ -> { drains.incrementAndGet(); return null; }).when(line).drain();
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            assertEquals(inputs.size(), drains.get());
            HttpRequest request = call.getArgument(0);
            var payload = new ByteArrayOutputStream();
            request.bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
                public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
                public void onNext(java.nio.ByteBuffer buffer) { byte[] bytes = new byte[buffer.remaining()]; buffer.get(bytes); payload.writeBytes(bytes); }
                public void onError(Throwable error) { throw new AssertionError(error); }
                public void onComplete() { }
            });
            inputs.add(new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload.toByteArray()).path("input").asText());
            @SuppressWarnings("unchecked") HttpResponse<InputStream> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2}));
            return CompletableFuture.completedFuture(response);
        });
        String reply = "Profitability, account details and risk. ".repeat(200) + "The final sentence.";
        var started = new java.util.concurrent.atomic.AtomicInteger();
        try (var voice = new AssistantVoice(client, () -> "test", _ -> line)) {
            voice.speak(reply, started::incrementAndGet);
        }
        assertEquals(reply, String.join("", inputs));
        assertEquals(AssistantVoice.speechChunks(reply).size(), drains.get());
        assertEquals(1, started.get());
    }

    @Test void stoppingBetweenChunksDoesNotRestartTheRemainingReply() throws Exception {
        var client = mock(HttpClient.class);
        var line = mock(SourceDataLine.class);
        stream(client, new ByteArrayInputStream(new byte[]{1, 2}));
        when(line.write(any(byte[].class), anyInt(), anyInt())).thenAnswer(call -> call.getArgument(2));
        try (var voice = new AssistantVoice(client, () -> "test", _ -> line)) {
            doAnswer(_ -> { voice.stopSpeaking(); return null; }).when(line).drain();
            voice.speak("Read the whole reply. ".repeat(300));
        }
        verify(client, times(1)).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

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
