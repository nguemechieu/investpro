package org.investpro;

import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Objects;

public class StrategyTesterView extends Parent {
    private static final Logger logger = LoggerFactory.getLogger(StrategyTesterView.class);

    public StrategyTesterView(@NotNull Exchange exchange) {
        super();
        ChoiceBox<String> strategyChoiceBox = new ChoiceBox<>();
        strategyChoiceBox.getItems().addAll("Indicator", "Expert Advisor");


        strategyChoiceBox.getSelectionModel().selectFirst();
        GridPane gridPane = new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.setPadding(new javafx.geometry.Insets(10, 10, 10, 10));

        gridPane.add(strategyChoiceBox, 0, 0);
        ChoiceBox<Object> expertAdviserChoiceBox = new ChoiceBox<>();
        expertAdviserChoiceBox.getSelectionModel().selectFirst();
        strategyChoiceBox.setOnAction(event -> {

            if (strategyChoiceBox.getValue().equals("Indicator")) {
                expertAdviserChoiceBox.getItems().addAll(
                        exchange.getIndicatorList()
                );

            } else if (strategyChoiceBox.getValue().equals("Expert Advisor")) {
                expertAdviserChoiceBox.getItems().addAll(
                        "Custom expert advisor",
                        "Moving Average",
                        "MACD",
                        "RSI",
                        "Stochastic");
            }

        });

        gridPane.add(expertAdviserChoiceBox, 1, 0);
        Label symbolLabel = new Label("Symbol:");
        symbolLabel.setText("Symbol:");
        gridPane.add(symbolLabel, 0, 1);
        ChoiceBox<Object> symbolChoiceBox = new ChoiceBox<>();
        try {
            if (exchange instanceof Oanda) {
                for (String symbol : ((Oanda) exchange).getAvailableSymbols().stream().map(c -> c.code).toList()) {
                    symbolChoiceBox.getItems().add(symbol.replace("_", "/"));
                }
            } else {
                for (String symbol : exchange.getAvailableSymbols().stream().map(c -> c.code).toList()) {
                    symbolChoiceBox.getItems().add(symbol + "/USD");
                }
            }


        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        symbolChoiceBox.getSelectionModel().selectFirst();

        gridPane.add(symbolChoiceBox, 1, 1);
        Label periodLabel = new Label("Period:");
        periodLabel.setText("Period:");
        gridPane.add(periodLabel, 0, 2);
        ChoiceBox<Object> periodChoiceBox = new ChoiceBox<>();
        periodChoiceBox.getItems().addAll(
                "1m",
                "3m",
                "5m",
                "15m",
                "30m",
                "1h",
                "2h",
                "4h",
                "6h",
                "8h",
                "12h",
                "1d",
                "3d",
                "1w",
                "M"
        );
        periodChoiceBox.getSelectionModel().selectFirst();
        gridPane.add(periodChoiceBox, 1, 2);
        Label useDateLabel = new Label("Use Date:");
        useDateLabel.setText("Use Date:");
        gridPane.add(useDateLabel, 0, 3);
        CheckBox useDateChoiceBox = new CheckBox();
        useDateChoiceBox.setSelected(true);

        gridPane.add(useDateChoiceBox, 1, 3);
        Label fromLabel = new Label();
        fromLabel.setText("From:");
        gridPane.add(fromLabel, 2, 3);

        DatePicker fromDatePicker = new DatePicker();
        fromDatePicker.setValue(
                LocalDate.now()
        );
        gridPane.add(fromDatePicker, 3, 3);
        Label toLabel = new Label();
        toLabel.setText("To:");
        gridPane.add(toLabel, 4, 3);

        DatePicker toDatePicker = new DatePicker();
        toDatePicker.setValue(
                LocalDate.now()
        );
        gridPane.add(toDatePicker, 5, 3);

        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(true);
        gridPane.add(progressIndicator, 1, 6);
        Label progressLabel = new Label();
        progressLabel.setText("Progress:");
        gridPane.add(progressLabel, 0, 6);
        Button startBtn = new Button();
        startBtn.setText("Start");
        gridPane.add(startBtn, 3, 6);
        StackPane pane = new StackPane(gridPane

        );

        setStyle("-fx-background-color: rgb(15, 125, 185,0.6);");
        gridPane.setVgap(20);
        gridPane.setHgap(20);
        gridPane.setPadding(new javafx.geometry.Insets(20, 20, 10, 20));
        pane.setPadding(new javafx.geometry.Insets(10, 10, 10, 10));
        Objects.requireNonNull(getClass().getResource("/app.css")).toExternalForm();
        getChildren().add(pane);

        startBtn.setOnAction(event -> {
            progressIndicator.setVisible(true);
            progressLabel.setText("Progress:");

            progressIndicator.setProgress(0);
        });
        progressIndicator.progressProperty().addListener((observable, oldValue, newValue) -> progressLabel.setText(String.format("%.2f%%", newValue.doubleValue() * 100)));

        startBtn.setOnMouseClicked(event -> {
            logger.info(strategyChoiceBox.getValue() + "is now running");
            if (startBtn.getText().equals("Start")) {
                startBtn.setText("Stop");
                progressIndicator.setVisible(true);
                progressLabel.setText("Progress:");
                progressIndicator.setProgress(0);

            } else {
                startBtn.setText("Start");
            }
        });
        pane.setPrefWidth(800);
        pane.setPrefHeight(300);

    }


}
