package org.investpro.ui.panels;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import org.investpro.ai.AssistantRuntime;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Interactive investor assistant, available before connecting an exchange or starting trading. */
public final class AiInteractionPanel extends BorderPane implements AutoCloseable {
    private final AssistantRuntime assistant;
    private final String conversation = "desktop:" + UUID.randomUUID();
    private final VBox messages = new VBox(14);
    private final ScrollPane transcript = new ScrollPane(messages);
    private final TextArea question = new TextArea();
    private final Button send = new Button("Send question");
    private final Button clear = new Button("New conversation");
    private final Label status = new Label();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "DesktopAssistant"); thread.setDaemon(true); return thread;
    });
    private volatile boolean closed;
    private final ExecutorService speechWorker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "AssistantReplyPlayback"); thread.setDaemon(true); return thread;
    });
    private volatile long speechRequest;
    private final org.investpro.ai.AssistantVoice voice;
    private final Button microphone = new Button("Speak");
    private final CheckBox speakReplies = new CheckBox("Read replies aloud");
    private final Button replay = new Button("Listen to reply");
    private boolean recording;
    private String lastReply = "";
    private byte[] attachedScreenshot;
    private final javafx.scene.image.ImageView screenshotPreview = new javafx.scene.image.ImageView();
    private final Button telegramScreenshot = new Button("Send screenshot to Telegram");

    public AiInteractionPanel(AssistantRuntime assistant) {
        this.assistant = assistant;
        voice = assistant.createVoice();
        speakReplies.setSelected(true);
        setPadding(new Insets(20));
        getStyleClass().add("ai-interaction-panel");
        Label title = new Label("Your trading & investment assistant");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        Label subtitle = new Label("Explore markets, profitability, strategies and your account — available while trading is stopped.");
        subtitle.setWrapText(true);
        status.setText(assistant.isConfigured() ? "Ready · OpenAI" : "Set OPENAI_API_KEY to enable answers");
        progress.setPrefSize(18, 18); progress.setVisible(false); progress.setManaged(false);
        HBox state = new HBox(10, progress, status);
        FlowPane prompts = new FlowPane(8, 8);
        for (String prompt : new String[]{"Compare spot and perpetual futures", "How do fees affect profitability?", "Explain my account balance", "Help assess my portfolio risk"}) {
            Button suggestion = new Button(prompt);
            suggestion.setOnAction(_ -> { question.setText(prompt); question.requestFocus(); });
            prompts.getChildren().add(suggestion);
        }
        VBox header = new VBox(9, title, subtitle, state, prompts);
        header.setPadding(new Insets(0, 0, 16, 0)); setTop(header);
        messages.setPadding(new Insets(12));
        transcript.setFitToWidth(true); transcript.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        setCenter(transcript);
        messages.heightProperty().addListener((_, _, _) -> transcript.setVvalue(1));
        question.setPromptText("Ask a market, investment or account question…  Ctrl+Enter to send");
        question.setStyle("-fx-prompt-text-fill: -text-muted;");
        question.setWrapText(true); question.setPrefRowCount(3);
        question.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.ENTER) { submit(); event.consume(); }
        });
        send.setOnAction(_ -> submit());
        clear.setOnAction(_ -> { assistant.reset(conversation); voice.stopSpeaking(); clearScreenshot(); lastReply = ""; replay.setDisable(true); messages.getChildren().clear(); welcome(); });
        microphone.setOnAction(_ -> toggleRecording());
        replay.setDisable(true); replay.setOnAction(_ -> speak(lastReply));
        Button stopAudio = new Button("Stop audio"); stopAudio.setOnAction(_ -> { speechRequest++; voice.stopSpeaking(); status.setText("Audio stopped."); });
        Button capture = new Button("Take screenshot");
        capture.setOnAction(_ -> {
            capture.setDisable(true); status.setText("Capturing InvestPro…");
            worker.execute(() -> {
                try {
                    byte[] png = assistant.captureScreenshot();
                    Platform.runLater(() -> {
                        if (closed) return;
                        attachedScreenshot = png;
                        screenshotPreview.setImage(new javafx.scene.image.Image(new java.io.ByteArrayInputStream(png)));
                        screenshotPreview.setVisible(true); screenshotPreview.setManaged(true); telegramScreenshot.setDisable(false);
                        status.setText("Screenshot attached. Send with your question, or send to Telegram.");
                    });
                } catch (Exception error) { Platform.runLater(() -> { if (!closed) status.setText("Screenshot unavailable. Keep the InvestPro window open."); }); }
                finally { Platform.runLater(() -> { if (!closed) capture.setDisable(false); }); }
            });
        });
        telegramScreenshot.setDisable(true);
        telegramScreenshot.setOnAction(_ -> {
            byte[] png = attachedScreenshot;
            if (png == null) return;
            telegramScreenshot.setDisable(true); status.setText("Sending screenshot to configured Telegram chat…");
            worker.execute(() -> {
                String result;
                try { result = assistant.sendScreenshot(png) ? "Screenshot sent to Telegram." : "Screenshot upload failed. Check Telegram token and configured chat."; }
                catch (Exception error) { result = "Screenshot upload failed. Please try again."; }
                String completed = result;
                Platform.runLater(() -> { if (!closed) { status.setText(completed); telegramScreenshot.setDisable(attachedScreenshot == null); } });
            });
        });
        Button removeScreenshot = new Button("Remove screenshot"); removeScreenshot.setOnAction(_ -> clearScreenshot());
        screenshotPreview.setFitWidth(300); screenshotPreview.setFitHeight(150); screenshotPreview.setPreserveRatio(true);
        screenshotPreview.setVisible(false); screenshotPreview.setManaged(false);
        FlowPane actions = new FlowPane(10, 8, send, clear, microphone, speakReplies, replay, stopAudio, capture, telegramScreenshot, removeScreenshot);
        Label context = new Label("Ask for trades, order cancellation or bot pause/resume. Review the action, then enter /confirm CODE to execute. Speak records up to 60 seconds; the transcript is editable before sending. Voice is AI-generated.");
        context.setWrapText(true);
        VBox composer = new VBox(9, screenshotPreview, question, actions, context);
        composer.setPadding(new Insets(16, 0, 0, 0)); setBottom(composer);
        welcome();
    }

    private void welcome() {
        message("InvestPro", "What would you like to understand? Ask about market opportunities, costs, risk or your account. For current market analysis, supply the symbol and data you want assessed.");
    }

    private Label message(String author, String text) {
        Label heading = new Label(author); heading.setStyle("-fx-font-weight: bold;");
        Label body = new Label(text); body.setWrapText(true); body.setMaxWidth(Double.MAX_VALUE);
        VBox card = new VBox(7, heading, body); card.setPadding(new Insets(14));
        card.setStyle("-fx-border-color: -fx-box-border; -fx-border-radius: 8; -fx-background-radius: 8;");
        messages.getChildren().add(card); return body;
    }

    private void submit() {
        String prompt = question.getText().trim();
        byte[] png = attachedScreenshot;
        if (prompt.isBlank() && png != null) prompt = "Explain what is visible in this InvestPro screenshot.";
        if (closed || send.isDisabled() || prompt.isBlank()) return;
        if (prompt.length() > 6000) { status.setText("Please shorten your question to 6000 characters."); return; }
        message("You", prompt); question.clear();
        Label reply = message("InvestPro", "Connecting…");
        send.setDisable(true); clear.setDisable(true);
        progress.setVisible(true); progress.setManaged(true); status.setText("Thinking…");
        String submittedPrompt = prompt;
        worker.execute(() -> {
            StringBuilder streamed = new StringBuilder();
            String answer;
            try {
                answer = assistant.ask(conversation, submittedPrompt, png, delta -> {
                    streamed.append(delta); String snapshot = streamed.toString();
                    Platform.runLater(() -> { if (!closed) { reply.setText(snapshot); status.setText("Receiving answer…"); } });
                });
            } catch (Exception error) { answer = "Unable to complete the request. Please try again."; }
            String completed = answer;
            Platform.runLater(() -> {
                if (closed) return;
                reply.setText(completed); send.setDisable(false); clear.setDisable(false);
                if (png != null && attachedScreenshot == png) clearScreenshot();
                lastReply = completed; replay.setDisable(completed.isBlank());
                progress.setVisible(false); progress.setManaged(false);
                status.setText(assistant.isConfigured() ? "Ready · OpenAI" : "Set OPENAI_API_KEY to enable answers");
                question.requestFocus();
                if (speakReplies.isSelected()) speak(completed);
            });
        });
    }

    private void toggleRecording() {
        if (!recording) {
            if (!assistant.isConfigured()) { status.setText("Configure OpenAI before using speech."); return; }
            try { voice.stopSpeaking(); voice.startRecording(); recording = true; microphone.setText("Finish recording"); status.setText("Listening (up to 60 seconds)…"); }
            catch (Exception error) { status.setText("Microphone unavailable. Check your audio device and microphone permissions."); }
        } else {
            recording = false; microphone.setDisable(true); microphone.setText("Speak"); status.setText("Transcribing…");
            worker.execute(() -> {
                try { String text = voice.stopAndTranscribe(); Platform.runLater(() -> { if (!closed) { question.setText(text); question.requestFocus(); status.setText("Review the transcript, then send."); } }); }
                catch (Exception error) { Platform.runLater(() -> { if (!closed) status.setText("Could not transcribe audio. Check OpenAI configuration and try again."); }); }
                finally { Platform.runLater(() -> { if (!closed) microphone.setDisable(false); }); }
            });
        }
    }
    private void speak(String text) {
        if (text.isBlank()) return;
        long request = ++speechRequest;
        voice.stopSpeaking(); status.setText("Preparing spoken reply…");
        speechWorker.execute(() -> {
            try {
                if (!closed && request == speechRequest) voice.speak(text);
                Platform.runLater(() -> { if (!closed && request == speechRequest) status.setText("Spoken reply finished."); });
            } catch (Exception error) {
                String reason = error instanceof javax.sound.sampled.LineUnavailableException || error instanceof IllegalArgumentException
                        ? "Speaker output unavailable. Check your default audio device and volume."
                        : error instanceof java.io.IOException || error instanceof IllegalStateException ? error.getMessage()
                        : "Speech playback failed (" + error.getClass().getSimpleName() + ").";
                Platform.runLater(() -> { if (!closed && request == speechRequest) status.setText(reason); });
            }
        });
    }
    public void stopAudio() {
        speechRequest++; voice.stopRecording(); voice.stopSpeaking(); recording = false; microphone.setText("Speak");
    }
    private void clearScreenshot() {
        attachedScreenshot = null; screenshotPreview.setImage(null); screenshotPreview.setVisible(false);
        screenshotPreview.setManaged(false); telegramScreenshot.setDisable(true);
    }
    @Override public void close() { closed = true; voice.close(); speechWorker.shutdownNow(); worker.shutdownNow(); assistant.reset(conversation); }
}
