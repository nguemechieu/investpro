package org.investpro.ai;

import org.junit.jupiter.api.Test;
import javax.sound.sampled.*;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistantVoicePlaybackTest {
    @Test void speechWritesPcmToSpeakersAndDrainsBeforeClosing() throws Exception {
        var client = mock(HttpClient.class); HttpResponse<byte[]> response = mock(HttpResponse.class);
        byte[] pcm = new byte[]{1, 2, 3, 4};
        when(response.statusCode()).thenReturn(200); when(response.body()).thenReturn(pcm);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var line = mock(SourceDataLine.class);
        when(line.write(eq(pcm), eq(0), eq(4))).thenReturn(4);
        try (var voice = new AssistantVoice(client, () -> "test", format -> line)) { voice.speak("Actual reply text"); }
        var format = org.mockito.ArgumentCaptor.forClass(AudioFormat.class);
        var order = inOrder(line);
        order.verify(line).open(format.capture()); order.verify(line).start();
        order.verify(line).write(pcm, 0, 4); order.verify(line).drain(); order.verify(line).close();
        assertEquals(24000f, format.getValue().getSampleRate()); assertEquals(16, format.getValue().getSampleSizeInBits());
        assertFalse(format.getValue().isBigEndian()); assertEquals(1, format.getValue().getChannels());
    }
    @Test void stopDuringSpeechGenerationPreventsLatePlayback() throws Exception {
        var client = mock(HttpClient.class); var line = mock(SourceDataLine.class);
        var voice = new AssistantVoice(client, () -> "test", format -> line);
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200); when(response.body()).thenReturn(new byte[]{1, 2});
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> { voice.stopSpeaking(); return response; });
        try (voice) { voice.speak("Reply"); }
        verifyNoInteractions(line);
    }
    @Test void emptyAudioReportsFailureRatherThanSilentSuccess() throws Exception {
        var client = mock(HttpClient.class); HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200); when(response.body()).thenReturn(new byte[0]);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var line = mock(SourceDataLine.class);
        try (var voice = new AssistantVoice(client, () -> "test", format -> line)) {
            assertThrows(java.io.IOException.class, () -> voice.speak("Reply"));
        }
        verifyNoInteractions(line);
    }
}
