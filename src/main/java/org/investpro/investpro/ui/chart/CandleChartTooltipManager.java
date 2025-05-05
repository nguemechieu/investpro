package org.investpro.investpro.ui.chart;

import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import org.investpro.investpro.model.CandleData;


public class CandleChartTooltipManager {

    private final Tooltip tooltip = new Tooltip();
    private Node currentTarget;

    public CandleChartTooltipManager() {
        tooltip.setAutoHide(true);
        tooltip.setWrapText(true);
        tooltip.setStyle("-fx-background-color: #2b2b2b; -fx-text-fill: white;");
    }

    public void install(Node target, CandleData candle) {
        uninstall();
        currentTarget = target;

        String content = formatTooltipText(candle);
        tooltip.setText(content);

        Tooltip.install(target, tooltip);

        // Optional: update tooltip position
        target.setOnMouseMoved(this::updatePosition);
    }

    public void uninstall() {
        if (currentTarget != null) {
            Tooltip.uninstall(currentTarget, tooltip);
            currentTarget.setOnMouseMoved(null);
            currentTarget = null;
        }
    }

    private void updatePosition(MouseEvent event) {
        tooltip.show(currentTarget, event.getScreenX() + 10, event.getScreenY() + 10);
    }

    private String formatTooltipText(CandleData candle) {
        return String.format("""
                        Time: %s
                        Open: %.2f
                        High: %.2f
                        Low: %.2f
                        Close: %.2f
                        Volume: %.2f
                        """,
                candle.getOpenTime(),
                candle.getOpenPrice(),
                candle.getHighPrice(),
                candle.getLowPrice(),
                candle.getClosePrice(),
                candle.getVolume()
        );
    }
}
