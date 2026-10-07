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
    private volatile SourceDataLine playback;
    @FunctionalInterface interface SpeakerFactory { SourceDataLine create(AudioFormat format) throws LineUnavailableException; }
    private final SpeakerFactory speakers;
    private CompletableFuture<byte[]> recording;
    private volatile boolean closed;
    private final java.util.concurrent.atomic.AtomicLong playbackGeneration = new java.util.concurrent.atomic.AtomicLong();
    public AssistantVoice(HttpClient client, Supplier<String> apiKey) { this(client, apiKey, AudioSystem::getSourceDataLine); }
    AssistantVoice(HttpClient client, Supplier<String> apiKey, SpeakerFactory speakers) {
        this.client = client; this.apiKey = apiKey; this.speakers = speakers;
    }

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
        if (closed || text == null || text.isBlank()) return;
        stopSpeaking();
        long generation = playbackGeneration.get();
        var json = new ObjectMapper();
        var body = json.createObjectNode().put("model", "gpt-4o-mini-tts").put("voice", "coral")
                .put("input", text.substring(0, Math.min(text.length(), 4000))).put("response_format", "pcm");
        byte[] pcm = send("speech", "application/json", json.writeValueAsBytes(body));
        if (closed || generation != playbackGeneration.get()) return;
        if (pcm.length == 0 || pcm.length % 2 != 0) throw new IOException("OpenAI returned empty or invalid speech audio.");
        AudioFormat format = new AudioFormat(24000, 16, 1, true, false);
        SourceDataLine line = speakers.create(format);
        try {
            line.open(format);
            if (line.isControlSupported(BooleanControl.Type.MUTE))
                ((BooleanControl) line.getControl(BooleanControl.Type.MUTE)).setValue(false);
            if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                gain.setValue(Math.clamp(0f, gain.getMinimum(), gain.getMaximum()));
            }
            synchronized (this) {
                if (closed || generation != playbackGeneration.get()) return;
                playback = line; line.start();
            }
            for (int offset = 0; offset < pcm.length && !closed && generation == playbackGeneration.get();) {
                int written = line.write(pcm, offset, Math.min(8192, pcm.length - offset));
                if (written <= 0) {
                    if (generation != playbackGeneration.get() || closed) return;
                    throw new IOException("Audio output stopped before playback completed.");
                }
                offset += written;
            }
            if (!closed && generation == playbackGeneration.get()) line.drain();
        } finally {
            synchronized (this) { if (playback == line) playback = null; }
            line.close();
        }
    }
    private byte[] send(String operation, String contentType, byte[] body) throws Exception {
        String key = apiKey.get();
        if (key == null || key.isBlank()) throw new IllegalStateException("OpenAI is not configured");
        var request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/audio/" + operation))
                .timeout(Duration.ofSeconds(45)).header("Authorization", "Bearer " + key)
                .header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) throw new IOException(switch (response.statusCode()) {
            case 401 -> "OpenAI speech authentication failed. Check OPENAI_API_KEY (HTTP 401).";
            case 403 -> "OpenAI speech access denied. Check audio model permissions (HTTP 403).";
            case 429 -> "OpenAI speech usage limit reached. Check quota or try again later (HTTP 429).";
            default -> "OpenAI audio request failed (HTTP " + response.statusCode() + ").";
        });
        return response.body();
    }
    public void stopRecording() { TargetDataLine line = microphone; microphone = null; if (line != null) { line.stop(); line.close(); } }
    public synchronized void stopSpeaking() { playbackGeneration.incrementAndGet(); SourceDataLine line = playback; playback = null; if (line != null) { line.stop(); line.flush(); line.close(); } }
    @Override public void close() { closed = true; stopRecording(); stopSpeaking(); }
}
