package org.investpro.investpro;

import javafx.scene.control.Tooltip;
import javafx.scene.Node;
import javafx.util.Duration;

public class TooltipHelper {

    public static void attachTooltip(Node node, String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setShowDelay(Duration.millis(50));
        Tooltip.install(node, tooltip);
    }
}
