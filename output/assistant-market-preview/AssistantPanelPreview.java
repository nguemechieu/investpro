import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.embed.swing.SwingFXUtils;
import org.investpro.ai.AssistantRuntime;
import org.investpro.ui.panels.AiInteractionPanel;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import javax.imageio.ImageIO;
import java.io.File;

public class AssistantPanelPreview {
    public static void main(String[] args) throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        Platform.startup(() -> {
            AssistantRuntime runtime = new AssistantRuntime(new Properties(), "", "");
            AiInteractionPanel panel = new AiInteractionPanel(runtime);
            try {
                Scene scene = new Scene(panel, 900, 720);
                scene.getStylesheets().add(AssistantPanelPreview.class.getResource("/css/app.css").toExternalForm());
                scene.getStylesheets().add(AssistantPanelPreview.class.getResource("/css/components.css").toExternalForm());
                panel.applyCss(); panel.layout();
                WritableImage image = panel.snapshot(null, new WritableImage(900, 720));
                ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", new File("output/assistant-market-preview/ai-panel.png"));
                System.out.println("Offline JavaFX panel rendered: 900x720");
            } catch (Throwable error) { failure.set(error); }
            finally { panel.close(); runtime.close(); ready.countDown(); }
        });
        if (!ready.await(30, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Preview timed out");
        Platform.exit();
        if (failure.get() != null) throw new RuntimeException(failure.get());
    }
}