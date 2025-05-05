package org.investpro.investpro;

import javafx.geometry.Orientation;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.jetbrains.annotations.NotNull;

public class Browser extends AnchorPane {

    private final TabPane tabPane = new TabPane();
    ToolBar mainToolbar = new ToolBar();
    private double zoomLevel = 1.0;

    public Browser() {
        setPrefSize(1540, 740);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);
        tabPane.setPrefSize(1500, 750);
        HBox.setHgrow(tabPane, Priority.ALWAYS);

        Button addTabButton = new Button("+");
        Button removeTabButton = new Button("-");

        addTabButton.setOnAction(_ -> addNewTab());
        removeTabButton.setOnAction(_ -> removeSelectedTab());

        mainToolbar.getItems().addAll(addTabButton, new Separator(Orientation.VERTICAL), removeTabButton);
        AnchorPane.setTopAnchor(tabPane, 40.0);

        getChildren().addAll(mainToolbar, new Separator(Orientation.HORIZONTAL), tabPane);

        addNewTab(); // Open one tab by default
    }

    private void addNewTab() {
        Tab tab = new Tab("Tab " + (tabPane.getTabs().size() + 1));
        tab.setContent(createWebViewPane());
        tab.setClosable(true);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
    }

    private void removeSelectedTab() {
        if (tabPane.getTabs().size() > 1) {
            tabPane.getTabs().remove(tabPane.getSelectionModel().getSelectedIndex());
        }
    }

    private @NotNull AnchorPane createWebViewPane() {
        AnchorPane container = new AnchorPane();
        container.setPrefSize(1540, 740);

        WebView webView = new WebView();
        WebEngine webEngine = webView.getEngine();

        TextField urlField = new TextField();
        urlField.setPromptText("Enter a URL");
        urlField.setPrefWidth(600);

        Button goButton = new Button("Go");
        goButton.setOnAction(_ -> loadUrl(webEngine, urlField.getText()));

        Button backButton = new Button("Back");
        backButton.setOnAction(_ -> {
            if (webEngine.getHistory().getCurrentIndex() > 0) {
                webEngine.getHistory().go(-1);
            }
        });

        Button forwardButton = new Button("Forward");
        forwardButton.setOnAction(_ -> {
            if (webEngine.getHistory().getCurrentIndex() < webEngine.getHistory().getEntries().size() - 1) {
                webEngine.getHistory().go(1);
            }
        });

        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(_ -> webEngine.reload());

        Button zoomInButton = new Button("+");
        zoomInButton.setOnAction(_ -> {
            zoomLevel = Math.min(zoomLevel + 0.1, 3.0);
            webView.setZoom(zoomLevel);
        });

        Button zoomOutButton = new Button("-");
        zoomOutButton.setOnAction(_ -> {
            zoomLevel = Math.max(zoomLevel - 0.1, 0.5);
            webView.setZoom(zoomLevel);
        });

        ToolBar urlBar = new ToolBar(goButton, backButton, forwardButton, refreshButton, zoomInButton, zoomOutButton, urlField);
        AnchorPane.setTopAnchor(urlBar, 0.0);
        AnchorPane.setTopAnchor(webView, 35.0);
        AnchorPane.setBottomAnchor(webView, 0.0);
        AnchorPane.setLeftAnchor(webView, 0.0);
        AnchorPane.setRightAnchor(webView, 0.0);

        webEngine.load("https://www.google.com");

        urlField.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getClickCount() == 2) {
                loadUrl(webEngine, urlField.getText());
            } else {
                urlField.clear();
                urlField.requestFocus();
                urlField.setCursor(Cursor.TEXT);
            }
        });

        container.getChildren().addAll(urlBar, webView);
        return container;
    }

    private void loadUrl(WebEngine engine, String input) {
        if (!input.startsWith("http")) {
            input = "https://" + input;
        }
        engine.load(input);
    }
}
