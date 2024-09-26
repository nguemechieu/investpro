package org.investpro;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

public class Browser extends Region {

    private double zoomLevel = 1.0; // Initial zoom level

    public Browser() {

        // WebView and WebEngine
        WebView webView = new WebView();
        WebEngine webEngine = webView.getEngine();

        // TextField for the URL bar
        TextField urlField = new TextField();
        urlField.setPrefWidth(600);

        // Load button
        Button goButton = new Button("Search");
        goButton.setOnAction(_ -> webEngine.load(urlField.getText()));

        // Back and Forward buttons
        Button backButton = new Button("Back");
        Button forwardButton = new Button("Forward");

        backButton.setOnAction(_ -> {
            if (webEngine.getHistory().getCurrentIndex() > 0) {
                webEngine.getHistory().go(-1);
            }
        });

        forwardButton.setOnAction(_ -> {
            if (webEngine.getHistory().getCurrentIndex() < webEngine.getHistory().getEntries().size() - 1) {
                webEngine.getHistory().go(1);
            }
        });

        // Refresh button
        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(_ -> webEngine.reload());

        // Zoom buttons
        Button zoomInButton = new Button("Zoom In");
        Button zoomOutButton = new Button("Zoom Out");

        zoomInButton.setOnAction(_ -> {
            zoomLevel += 0.1;
            webView.setZoom(zoomLevel);
        });

        zoomOutButton.setOnAction(_ -> {
            zoomLevel = Math.max(zoomLevel - 0.1, 0.5); // Set the minimum zoom level to 0.5
            webView.setZoom(zoomLevel);
        });

        // Status bar for page loading
        ProgressBar progressBar = new ProgressBar();
        progressBar.setPrefWidth(600);
        Text statusText = new Text();

        // Track loading progress
        webEngine.getLoadWorker().progressProperty().addListener((obs, oldProgress, newProgress) -> {
            progressBar.setProgress(newProgress.doubleValue());
        });

        // Update status bar based on load state
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            switch (newState) {
                case SUCCEEDED -> statusText.setText("Page loaded successfully");
                case RUNNING -> statusText.setText("Loading...");
                case FAILED -> statusText.setText("Failed to load the page");
                default -> statusText.setText("");
            }
        });

        // Add keyboard shortcuts for navigation

        if (webEngine.getHistory().getCurrentIndex() < webEngine.getHistory().getEntries().size() - 1) {
                webEngine.getHistory().go(1);
            }


            if (webEngine.getHistory().getCurrentIndex() > 0) {
                webEngine.getHistory().go(-1);
            }




        // Set up the layout
        HBox controls = new HBox(5, urlField, goButton, backButton, forwardButton, refreshButton, zoomInButton, zoomOutButton);
        BorderPane root = new BorderPane();
        root.setTop(controls);
        root.setCenter(webView);

        // Status bar at the bottom
        HBox statusBar = new HBox(10, progressBar, statusText);
        statusBar.setAlignment(Pos.CENTER);
        root.setBottom(statusBar);
        setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);

        // Load a default URL
        webEngine.load("https://www.google.com");

        root.setPrefSize(1540, 740);

        // Add everything to the main pane
        getChildren().add(root);


    }
}
