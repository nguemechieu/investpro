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
    @Test void manualPlaybackSurvivesNewAnswersAndWindowHidingCancelsIt() throws Exception {
        new JFXPanel();
        var runtime = mock(AssistantRuntime.class);
        var voice = mock(AssistantVoice.class);
        var preferences = mock(Preferences.class);
        when(runtime.createVoice()).thenReturn(voice);
        when(runtime.isConfigured()).thenReturn(true);
        when(preferences.getDouble("voicePlaybackGainDb", 4)).thenReturn(4.0);
        when(voice.getPlaybackGainDb()).thenReturn(4.0f);
        String complete = "Full response. ".repeat(500) + "Final sentence.";
        var answerReady = new CountDownLatch(1);
        var finishAnswer = new CountDownLatch(1);
        when(runtime.ask(anyString(), anyString(), nullable(byte[].class), any())).thenAnswer(call -> {
            Consumer<String> delta = call.getArgument(3);
            delta.accept("Partial preview"); answerReady.countDown(); finishAnswer.await(); return complete;
        });
        var started = new CountDownLatch(1);
        var cancelled = new CountDownLatch(1);
        doAnswer(call -> {
            assertFalse(Platform.isFxApplicationThread());
            assertEquals(complete, call.getArgument(0)); started.countDown();
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException error) { cancelled.countDown(); throw error; }
            return null;
        }).when(voice).speak(anyString(), any(Runnable.class));
        AiInteractionPanel panel = fx(() -> new AiInteractionPanel(runtime, preferences));
        try {
            fx(() -> { assertFalse(((CheckBox) panel.lookup("#voice-auto-read")).isSelected()); submit(panel); return null; });
            assertTrue(answerReady.await(5, TimeUnit.SECONDS));
            verify(voice, never()).speak(anyString(), any(Runnable.class));
            finishAnswer.countDown();
            verify(runtime, timeout(5000)).ask(anyString(), anyString(), nullable(byte[].class), any());
            // Wait for the final answer's JavaFX callback without blocking the UI thread.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (fx(() -> ((Button) panel.lookup("#voice-read-aloud")).isDisabled()) && System.nanoTime() < deadline) Thread.sleep(10);
            fx(() -> { ((Button) panel.lookup("#voice-read-aloud")).fire(); return null; });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            int stops = mockingDetails(voice).getInvocations().stream().filter(i -> i.getMethod().getName().equals("stopSpeaking")).toList().size();
            fx(() -> {
                assertFalse(((Button) panel.lookup("#voice-stop")).isDisabled());
                submit(panel); return null;
            });
            assertEquals(stops, mockingDetails(voice).getInvocations().stream().filter(i -> i.getMethod().getName().equals("stopSpeaking")).count());
            assertEquals("heartbeat", fx(() -> "heartbeat"));
            fx(() -> { panel.stopAudio(); assertTrue(((Button) panel.lookup("#voice-stop")).isDisabled()); return null; });
            assertTrue(cancelled.await(5, TimeUnit.SECONDS));
            var failed = new CountDownLatch(1);
            doAnswer(_ -> { failed.countDown(); throw new java.io.IOException("Sensitive server response"); })
                    .when(voice).speak(anyString(), any(Runnable.class));
            fx(() -> { ((Button) panel.lookup("#voice-read-aloud")).fire(); return null; });
            assertTrue(failed.await(5, TimeUnit.SECONDS));
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!fx(() -> ((Button) panel.lookup("#voice-stop")).isDisabled()) && System.nanoTime() < deadline) Thread.sleep(10);
            fx(() -> {
                assertTrue(((Button) panel.lookup("#voice-stop")).isDisabled());
                assertFalse(((Button) panel.lookup("#voice-read-aloud")).isDisabled());
                assertEquals("Read Aloud", ((Button) panel.lookup("#voice-read-aloud")).getText());
                var header = (javafx.scene.layout.VBox) panel.getTop();
                var state = (javafx.scene.layout.HBox) header.getChildren().get(2);
                String status = ((Label) state.getChildren().get(1)).getText();
                assertTrue(status.contains("Could not read the reply"));
                assertFalse(status.contains("Sensitive"));
                return null;
            });
        } finally { finishAnswer.countDown(); fx(() -> { panel.close(); return null; }); }
    }

    private static void submit(AiInteractionPanel panel) {
        ((TextArea) panel.lookup("TextArea")).setText("Analyze my market");
        panel.actions.getChildren().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> "Send question".equals(button.getText())).findFirst().orElseThrow().fire();
    }
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
        when(preferences.getBoolean("voiceAutoRead", false)).thenReturn(true);
        when(voice.getPlaybackGainDb()).thenReturn(4.0f);
        when(runtime.createVoice()).thenReturn(voice); when(runtime.isConfigured()).thenReturn(true);
        String fullReply = "Entire final paragraph. ".repeat(400) + "Last important sentence.";
        var previewReceived = new CountDownLatch(1);
        var finishResponse = new CountDownLatch(1);
        when(runtime.ask(anyString(), anyString(), nullable(byte[].class), any())).thenAnswer(call -> {
            Consumer<String> delta = call.getArgument(3); delta.accept("Short streamed preview.");
            previewReceived.countDown(); finishResponse.await(); return fullReply;
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
            assertTrue(previewReceived.await(5, TimeUnit.SECONDS));
            assertNull(calls.poll(150, TimeUnit.MILLISECONDS));
            finishResponse.countDown();
            assertEquals(fullReply, calls.poll(10, TimeUnit.SECONDS));
            fx(() -> {
                ((Button) panel.lookup("#voice-read-aloud")).fire();
                ((Slider) panel.lookup("#voice-volume")).setValue(6);
                return null;
            });
            assertEquals(fullReply, calls.poll(10, TimeUnit.SECONDS));
            verify(voice).setPlaybackGainDb(6); verify(preferences).putDouble("voicePlaybackGainDb", 6);
            fx(() -> {
                var messages = (javafx.scene.layout.VBox) ((ScrollPane) panel.getCenter()).getContent();
                var card = (javafx.scene.layout.VBox) messages.getChildren().getLast();
                var header = (javafx.scene.layout.HBox) card.getChildren().getFirst();
                header.getChildren().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(button -> "Read Aloud".equals(button.getText())).findFirst().orElseThrow().fire();
                return null;
            });
            assertEquals(fullReply, calls.poll(10, TimeUnit.SECONDS));
        } finally { finishResponse.countDown(); fx(() -> { panel.close(); return null; }); }
    }
}
