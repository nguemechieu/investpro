package org.investpro;

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

    private double zoomLevel = 1.0; // Initial zoom level

    ToolBar toolbar = new ToolBar();
    ToolBar toolbar2 = new ToolBar();
    TabPane tabPane = new TabPane();
    public Browser() {

        // TextField for the URL bar
        TextField urlField = new TextField();
        urlField.setPrefWidth(600);

        // Set up the layout
        Button btnAdd = new Button("+");
        Button btnRemove = new Button("-");

        btnAdd.setOnAction(_ -> {
            Browser newBrowser = new Browser();
            getChildren().add(newBrowser);
        });

        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);
        tabPane.setPrefSize(1500, 750);

        HBox.setHgrow(tabPane, Priority.ALWAYS);
        toolbar2.setTranslateY(700);
        toolbar2.setTranslateX(0);
        HBox.setHgrow(toolbar2, Priority.ALWAYS);

        final int[] index = {0};
        AnchorPane.setTopAnchor(tabPane, 0.0);

        btnAdd.setOnAction(_ -> {
            index[0]++;
            Tab tab = new Tab("Tab " + index[0]);
            WebView webView = new WebView();
            WebEngine webEngine = webView.getEngine();
            webEngine.loadContent("<html><body>Hello, World!</body></html>");
            tab.setClosable(true);
            tab.setContent(DisplayWebBrowser());
            tabPane.getTabs().add(tab);
            tabPane.getSelectionModel().select(tabPane.getTabs().size() - 1);
        });

        btnRemove.setOnAction(_ -> {
            if (tabPane.getTabs().size() > 1) {
                tabPane.getTabs().remove(tabPane.getSelectionModel().getSelectedIndex());
            }
        });

        toolbar.getItems().addAll(btnAdd, btnRemove);
        toolbar.setTranslateY(0);
        tabPane.setTranslateY(40);

        getChildren().addAll(toolbar, new Separator(Orientation.HORIZONTAL), tabPane);
        setPrefSize(1540, 740);
    }

    private @NotNull AnchorPane DisplayWebBrowser() {
        AnchorPane anchorPane = new AnchorPane();
        anchorPane.setPrefSize(1540, 740);

        WebView webView = new WebView();
        WebEngine webEngine = webView.getEngine();

        TextField urlField = new TextField();
        urlField.setPromptText("Enter a URL");
        urlField.setPrefWidth(600);

        Button goButton = new Button("Search");
        goButton.setOnAction(_ -> {
            String url = urlField.getText().startsWith("http") ? urlField.getText() : "https://" + urlField.getText();
            webEngine.load(url);
        });

        urlField.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getClickCount() == 2) {
                String url = urlField.getText().startsWith("http") ? urlField.getText() : "https://" + urlField.getText();
                webEngine.load(url);
            } else {
                urlField.clear();
                urlField.requestFocus();
                urlField.setCursor(Cursor.TEXT);
            }
        });

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


        ToolBar toolbar2 = new ToolBar();
        toolbar2.getItems().addAll(goButton, backButton, forwardButton, refreshButton, zoomInButton, zoomOutButton);

        HBox tools = new HBox(10, toolbar2, new Separator(Orientation.HORIZONTAL), urlField);
        tools.setTranslateX(0);
        tools.setTranslateY(0);

        anchorPane.getChildren().addAll(tools, new Separator(Orientation.HORIZONTAL), webView);

        webView.setPrefSize(1500, 740);
        webEngine.load("https://www.google.com");

        return anchorPane;
    }
}
