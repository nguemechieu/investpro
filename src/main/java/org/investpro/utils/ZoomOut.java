package org.investpro.utils;

import javafx.animation.ScaleTransition;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility for zooming out JavaFX chart/workspace nodes.
 *
 * Designed for InvestPro chart tabs, chart containers, WebView panels,
 * and any JavaFX Node that supports scale transforms.
 */
public final class ZoomOut {

    private static final Logger logger = LoggerFactory.getLogger(ZoomOut.class);

    public static final double DEFAULT_STEP = 0.10;
    public static final double DEFAULT_MIN_SCALE = 0.50;
    public static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(140);

    private ZoomOut() {
        // Utility class
    }

    public static void apply(Node node) {
        apply(node, DEFAULT_STEP, DEFAULT_MIN_SCALE, true);
    }

    public static void apply(Node node, double step) {
        apply(node, step, DEFAULT_MIN_SCALE, true);
    }

    public static void apply(Node node, double step, double minScale) {
        apply(node, step, minScale, true);
    }

    public static void apply(Node node, double step, double minScale, boolean animated) {
        if (node == null) {
            return;
        }

        double safeStep = sanitizeStep(step);
        double safeMinScale = sanitizeMinScale(minScale);

        double currentX = node.getScaleX() <= 0 ? 1.0 : node.getScaleX();
        double currentY = node.getScaleY() <= 0 ? 1.0 : node.getScaleY();

        double targetX = Math.max(safeMinScale, currentX - safeStep);
        double targetY = Math.max(safeMinScale, currentY - safeStep);

        if (animated) {
            animateScale(node, targetX, targetY);
        } else {
            node.setScaleX(targetX);
            node.setScaleY(targetY);
        }

        logger.debug("Zoomed out {} to scaleX={} scaleY={}",
                node.getClass().getSimpleName(),
                targetX,
                targetY
        );
    }

    public static void applyToScrollContent(ScrollPane scrollPane) {
        applyToScrollContent(scrollPane, DEFAULT_STEP, DEFAULT_MIN_SCALE, true);
    }

    public static void applyToScrollContent(
            ScrollPane scrollPane,
            double step,
            double minScale,
            boolean animated
    ) {
        if (scrollPane == null || scrollPane.getContent() == null) {
            return;
        }

        apply(scrollPane.getContent(), step, minScale, animated);
    }

    public static void reset(Node node) {
        reset(node, true);
    }

    public static void reset(Node node, boolean animated) {
        if (node == null) {
            return;
        }

        if (animated) {
            animateScale(node, 1.0, 1.0);
        } else {
            node.setScaleX(1.0);
            node.setScaleY(1.0);
        }

        logger.debug("Zoom reset for {}", node.getClass().getSimpleName());
    }

    public static boolean canZoomOut(Node node) {
        return canZoomOut(node, DEFAULT_MIN_SCALE);
    }

    public static boolean canZoomOut(Node node, double minScale) {
        if (node == null) {
            return false;
        }

        double safeMinScale = sanitizeMinScale(minScale);
        double current = Math.min(node.getScaleX(), node.getScaleY());

        return current > safeMinScale;
    }

    public static double currentZoomPercent(Node node) {
        if (node == null) {
            return 100.0;
        }

        double scale = Math.max(node.getScaleX(), node.getScaleY());

        if (scale <= 0) {
            scale = 1.0;
        }

        return scale * 100.0;
    }

    private static void animateScale(Node node, double targetX, double targetY) {
        ScaleTransition transition = new ScaleTransition(DEFAULT_ANIMATION_DURATION, node);
        transition.setToX(targetX);
        transition.setToY(targetY);
        transition.play();
    }

    private static double sanitizeStep(double step) {
        if (!Double.isFinite(step) || step <= 0) {
            return DEFAULT_STEP;
        }

        return Math.min(step, 0.75);
    }

    private static double sanitizeMinScale(double minScale) {
        if (!Double.isFinite(minScale) || minScale <= 0) {
            return DEFAULT_MIN_SCALE;
        }

        return Math.min(minScale, 1.0);
    }
}