package org.investpro.chart;

import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import lombok.Getter;
import lombok.Setter;
import org.investpro.Exchange;

@Getter
@Setter
public class ChartLayout extends StackPane {

    private final Exchange exchange;
    private final CandleStickChart candleStickChart;
    private final Group drawingLayer;
    private final Button exportButton;

    public ChartLayout(Exchange exchange) {
        this.exchange = exchange;

        // Create the main chart
        candleStickChart = new CandleStickChart(exchange);

        // Create drawing layer on top of the chart
        drawingLayer = new Group();

        // Create Export button
        exportButton = new Button("Export Chart");
        exportButton.setOnAction(e -> ChartExporter.exportAsPng(candleStickChart));
        exportButton.setTranslateX(10); // Move button slightly
        exportButton.setTranslateY(10);

        // Create a container that stacks chart + drawing layer + export button
        Pane chartContainer = new Pane(candleStickChart, drawingLayer, exportButton);

        getChildren().add(chartContainer);
        setPrefSize(1200, 800); // Set preferred size for full screen
    }
}
