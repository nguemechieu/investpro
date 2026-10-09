package org.investpro.ui.panels;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.control.*;
import org.investpro.ai.AssistantRuntime;
import org.investpro.ai.AssistantVoice;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiInteractionVoiceTest {
    private static <T> T fx(Callable<T> operation) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> { try { result.complete(operation.call()); } catch (Throwable error) { result.completeExceptionally(error); } });
        return result.get(10, TimeUnit.SECONDS);
    }

    @Test void automaticAndReplaySpeechUseTheCompleteFinalAnswerOffTheFxThread() throws Exception {
        new JFXPanel();
        var runtime = mock(AssistantRuntime.class); var voice = mock(AssistantVoice.class);
        var preferences = mock(Preferences.class);
        when(preferences.getDouble("voicePlaybackGainDb", 4)).thenReturn(4.0);
        when(voice.getPlaybackGainDb()).thenReturn(4.0f);
        when(runtime.createVoice()).thenReturn(voice); when(runtime.isConfigured()).thenReturn(true);
        String fullReply = "Entire final paragraph. ".repeat(400) + "Last important sentence.";
        when(runtime.ask(anyString(), anyString(), nullable(byte[].class), any())).thenAnswer(call -> {
            Consumer<String> delta = call.getArgument(3); delta.accept("Short streamed preview."); return fullReply;
        });
        var calls = new LinkedBlockingQueue<String>();
        doAnswer(call -> {
            assertFalse(Platform.isFxApplicationThread()); calls.add(call.getArgument(0));
            return null;
        }).when(voice).speak(anyString(), any(Runnable.class));
        AiInteractionPanel panel = fx(() -> new AiInteractionPanel(runtime, preferences));
        try {
            fx(() -> {
                ((TextArea) panel.lookup("TextArea")).setText("Analyze my market");
                panel.actions.getChildren().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(button -> "Send question".equals(button.getText())).findFirst().orElseThrow().fire();
                return null;
            });
            assertEquals(fullReply, calls.poll(10, TimeUnit.SECONDS));
            fx(() -> {
                panel.actions.getChildren().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(button -> "Listen to reply".equals(button.getText())).findFirst().orElseThrow().fire();
                ((Slider) panel.lookup("#voice-volume")).setValue(8);
                return null;
            });
            assertEquals(fullReply, calls.poll(10, TimeUnit.SECONDS));
            verify(voice).setPlaybackGainDb(8); verify(preferences).putDouble("voicePlaybackGainDb", 8);
        } finally { fx(() -> { panel.close(); return null; }); }
    }
}
