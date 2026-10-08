package org.investpro.ai;

import javafx.application.Platform;
import javafx.scene.Scene;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.imageio.ImageIO;

/** Captures only the InvestPro scene; encoding happens on the caller's background thread. */
public final class AppScreenshot {
    private AppScreenshot() { }
    public static byte[] capture(Supplier<Scene> source) throws Exception {
        return captureImage(() -> {
            Scene scene = source.get();
            if (scene == null) throw new IllegalStateException("InvestPro window is unavailable");
            return scene.snapshot(null);
        });
    }

    public static byte[] captureNode(Supplier<javafx.scene.Node> source) throws Exception {
        return captureImage(() -> {
            var node = source.get();
            if (node == null || node.getScene() == null) throw new IllegalStateException("No chart is open");
            return node.snapshot(null, null);
        });
    }

    private static byte[] captureImage(Supplier<javafx.scene.image.WritableImage> source) throws Exception {
        if (Platform.isFxApplicationThread()) throw new IllegalStateException("Capture must run on a worker");
        var pixels = new CompletableFuture<BufferedImage>();
        Platform.runLater(() -> {
            if (pixels.isDone()) return;
            try {
                var image = source.get();
                int width = (int) image.getWidth(), height = (int) image.getHeight();
                int[] argb = new int[width * height];
                image.getPixelReader().getPixels(0, 0, width, height,
                        javafx.scene.image.PixelFormat.getIntArgbInstance(), argb, 0, width);
                var buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                buffered.setRGB(0, 0, width, height, argb, 0, width); pixels.complete(buffered);
            } catch (Exception error) { pixels.completeExceptionally(error); }
        });
        try (var png = new ByteArrayOutputStream()) {
            ImageIO.write(pixels.get(10, TimeUnit.SECONDS), "png", png);
            if (png.size() > 10 * 1024 * 1024) throw new IllegalStateException("Screenshot exceeds 10 MB");
            return png.toByteArray();
        } finally { pixels.cancel(false); }
    }
}
