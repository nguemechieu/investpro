package org.investpro.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sound.sampled.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Push-to-talk capture and opt-in OpenAI speech, independent of trading lifecycle. */
public final class AssistantVoice implements AutoCloseable {
    private final HttpClient client;
    private final Supplier<String> apiKey;
    private volatile TargetDataLine microphone;
    private volatile Clip playback;
    private CompletableFuture<byte[]> recording;
    private volatile boolean closed;
    private final java.util.concurrent.atomic.AtomicLong playbackGeneration = new java.util.concurrent.atomic.AtomicLong();
    public AssistantVoice(HttpClient client, Supplier<String> apiKey) { this.client = client; this.apiKey = apiKey; }

    public synchronized void startRecording() throws Exception {
        if (closed) throw new IllegalStateException("Voice panel closed");
        if (microphone != null) throw new IllegalStateException("Already recording");
        AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
        TargetDataLine line = AudioSystem.getTargetDataLine(format);
        try { line.open(format); line.start(); } catch (Exception error) { line.close(); throw error; }
        microphone = line;
        recording = CompletableFuture.supplyAsync(() -> {
            try (var pcm = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[3200];
                while (line.isOpen() && pcm.size() < 16000 * 2 * 60) {
                    int count = line.read(buffer, 0, buffer.length);
                    if (count > 0) pcm.write(buffer, 0, count);
                }
                byte[] data = pcm.toByteArray();
                if (data.length == 0) throw new IllegalStateException("No microphone audio captured");
                try (var wave = new ByteArrayOutputStream(); var audio = new AudioInputStream(
                        new ByteArrayInputStream(data), format, data.length / format.getFrameSize())) {
                    AudioSystem.write(audio, AudioFileFormat.Type.WAVE, wave); return wave.toByteArray();
                }
            } catch (IOException error) { throw new java.io.UncheckedIOException(error); }
            finally { line.close(); }
        });
    }

    public synchronized String stopAndTranscribe() throws Exception {
        if (recording == null) throw new IllegalStateException("No recording");
        stopRecording();
        byte[] wave = recording.get(5, java.util.concurrent.TimeUnit.SECONDS);
        recording = null;
        return transcribe(wave);
    }
    public String transcribe(byte[] wave) throws Exception {
        String boundary = "InvestPro" + UUID.randomUUID();
        var multipart = new ByteArrayOutputStream();
        multipart.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\n"
                + "gpt-4o-mini-transcribe\r\n--" + boundary
                + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"question.wav\"\r\n"
                + "Content-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        multipart.write(wave); multipart.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        byte[] result = send("transcriptions", "multipart/form-data; boundary=" + boundary, multipart.toByteArray());
        return new ObjectMapper().readTree(result).path("text").asText();
    }

    public void speak(String text) throws Exception {
        if (closed) return;
        stopSpeaking();
        long generation = playbackGeneration.get();
        var json = new ObjectMapper();
        var body = json.createObjectNode().put("model", "gpt-4o-mini-tts").put("voice", "coral")
                .put("input", text.substring(0, Math.min(text.length(), 4000))).put("response_format", "wav");
        byte[] wave = send("speech", "application/json", json.writeValueAsBytes(body));
        if (closed || generation != playbackGeneration.get()) return;
        try (var input = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wave))) {
            Clip clip = AudioSystem.getClip();
            try {
                clip.open(input);
                clip.addLineListener(event -> { if (event.getType() == LineEvent.Type.STOP) clip.close(); });
                synchronized (this) {
                    if (closed || generation != playbackGeneration.get()) { clip.close(); return; }
                    playback = clip; clip.start();
                }
            } catch (Exception error) { clip.close(); throw error; }
        }
    }
    private byte[] send(String operation, String contentType, byte[] body) throws Exception {
        String key = apiKey.get();
        if (key == null || key.isBlank()) throw new IllegalStateException("OpenAI is not configured");
        var request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/audio/" + operation))
                .timeout(Duration.ofSeconds(45)).header("Authorization", "Bearer " + key)
                .header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) throw new IOException("OpenAI audio request failed (HTTP " + response.statusCode() + ")");
        return response.body();
    }
    public void stopRecording() { TargetDataLine line = microphone; microphone = null; if (line != null) { line.stop(); line.close(); } }
    public synchronized void stopSpeaking() { playbackGeneration.incrementAndGet(); Clip clip = playback; playback = null; if (clip != null) { clip.stop(); clip.close(); } }
    @Override public void close() { closed = true; stopRecording(); stopSpeaking(); }
}
