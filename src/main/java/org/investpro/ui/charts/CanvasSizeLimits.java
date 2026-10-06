package org.investpro.ui.charts;

/** Bounds Canvas backing textures, whose dimensions are measured in device pixels. */
final class CanvasSizeLimits {
    // JavaFX's default Prism texture limit. Stay within it without internal APIs.
    private static final double MAX_TEXTURE_PIXELS = 4096;

    private CanvasSizeLimits() {
    }

    static double limit(double requested, double fallback, double renderScale) {
        double scale = Double.isFinite(renderScale) && renderScale > 0 ? renderScale : 1;
        double maximum = Math.floor(MAX_TEXTURE_PIXELS / Math.max(1, scale));
        double size = Double.isFinite(requested) && requested > 0 ? requested : fallback;
        return Math.max(1, Math.min(size, maximum));
    }
}
