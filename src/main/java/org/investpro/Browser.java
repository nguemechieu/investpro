package org.investpro;

import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class Browser extends Region {
    private final WebEngine webEngine=new WebEngine();
    private final TabPane tabPane = new TabPane();
    private final WebView webView=new WebView();
    public Browser() {

        this.getStyleClass().add("browser");
        this.setPadding(new Insets(10, 10, 10, 10));
        this.setMaxWidth(Double.MAX_VALUE);
        this.setMaxHeight(Double.MAX_VALUE);

tabPane.setTranslateY(34);
        webEngine.load("https://www.google.com/search?q=");
        webView.setPrefSize(1520, 780);
        WebEngine webEngine = webView.getEngine();
        webEngine.setJavaScriptEnabled(true);
        webEngine.getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == Worker.State.SUCCEEDED) {
                webEngine.executeScript("window.scrollTo(0, document.body.scrollHeight)");}
        });

        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);
        int i=0;
        i++;
        DraggableTab tab = new DraggableTab("Tab" + i);
        tabPane.getTabs().add(tab);
        TextField searchBar =new TextField();
        searchBar.setPromptText("Search here... ");

        searchBar.setPrefWidth(1520);
        searchBar.textProperty().addListener((observable, oldValue, newValue) -> webEngine.load(
                "https://www.google.com/search?q=" + newValue
        ));
        WebView web=new WebView();
        web.setPrefSize(1520, 780);
        web.getEngine().load(
                "https://www.google.com/search?q="
        );

        tabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue!= null) {
                webEngine.load(
                        searchBar.getText());
                tab.setContent(new VBox(searchBar,
                        web,
                        new HBox(
                                createLink("https://www.google.com/"),
                                createLink("https://www.youtube.com/"),
                                createLink("https://www.facebook.com/"),
                                createLink("https://twitter.com/"),
                                createLink("https://www.instagram.com/"),
                                createLink("https://www.linkedin.com/"),
                                createLink("https://github.com/"),
                                createLink("https://www.reddit.com/")
                        )));
            }else {
                webView.getEngine().load(
                        "https://www.google.com/search?q="
                );
                tab.setContent(webView);
            }

        });
      webEngine.getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
          if (newValue == Worker.State.SUCCEEDED) {
              webEngine.executeScript("window.scrollTo(0, document.body.scrollHeight)");

          }else {
              webEngine.executeScript("window.scrollTo(0, 0)");
          }
      });
        tab.setContent(new VBox(searchBar,
                        web,
                        new HBox(
                                createLink("https://www.google.com/"),
                                createLink("https://www.youtube.com/"),
                                createLink("https://www.facebook.com/"),
                                createLink("https://twitter.com/"),
                                createLink("https://www.instagram.com/"),
                                createLink("https://www.linkedin.com/"),
                                createLink("https://github.com/"),
                                createLink("https://www.reddit.com/")
                        )
                )
        );
        HBox hBox = new HBox();
        hBox.setPadding(new Insets(10, 10, 10, 10));
        hBox.getChildren().add(downloadButton());
        hBox.getChildren().add(removeButton());
        Button addButton = new Button("+");
        hBox.getChildren().add(addButton);
        addButton.setOnAction(e -> {
            tabPane.getTabs().add(new DraggableTab("Tab" + tabPane.getTabs().size()));
            WebView web0 = new WebView();
            web0.setPrefSize(1530, 780);

            tabPane.getTabs().get(tabPane.getTabs().size() - 1).setContent(new VBox(searchBar, new Separator(Orientation.VERTICAL), web0));
        });
        hBox.getChildren().add(downloadButton());
        hBox.getChildren().add(refreshButton());
        hBox.getChildren().add(goBack());
        hBox.getChildren().add(goForward());
        hBox.getChildren().add(screenShotButton());
       hBox.getChildren().add(createLink("https://www.google.com/"));
       hBox.getChildren().add(createLink("https://www.youtube.com/"));
       hBox.getChildren().add(createLink("https://www.facebook.com/"));
       hBox.getChildren().add(createLink("https://twitter.com/"));
       hBox.getChildren().add(createLink("https://www.instagram.com/"));
       hBox.getChildren().add(createLink("https://www.linkedin.com/"));
       hBox.getChildren().add(createLink("https://github.com/"));
       hBox.getChildren().add(createLink("https://www.reddit.com/"));
getChildren().add(hBox);
        getChildren().add(tabPane);
    }


    private @NotNull Button downloadButton(){
        Button button = new Button("Download");
        button.setOnAction(e -> webEngine.executeScript("window.print()"));
        return button;

}

    private @NotNull Button removeButton() {
        Button button = new Button("-");
        button.setOnAction(e -> tabPane.getTabs().remove(tabPane.getSelectionModel().getSelectedIndex()));

        return button;
    }

    private @NotNull Button refreshButton() {
        Button button = new Button("Refresh");
        button.setOnAction(e -> webEngine.reload());
        return button;
    }


    private @NotNull Node createLink(String url) {
        Hyperlink link = new Hyperlink(url);
        link.setOnAction(e -> webEngine.load(url));
        return link;
    }
    private @NotNull  Button goBack () {
        Button button = new Button("Go Back");
        button.setOnAction(e -> webEngine.getHistory().go(-1));
        return button;
    }
    private @NotNull  Button goForward () {
        Button button = new Button("Go Forward");
        button.setOnAction(e -> webEngine.getHistory().go(1));
        return button;
    }

    private @NotNull Button screenShotButton() {
        Button button = new Button("Screen Shot");
        button.setOnAction(e -> {
            webEngine.executeScript("window.print()");
            webEngine.getDocument().createElement("img");
            Screenshot.capture(new File("screenshot.png"));

        });
        return button;
    }
    private @NotNull Node createSpacer() {
        Region region = new Region();
        region.getStyleClass().add("app");
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    @Override
    public Node getStyleableNode() {
        return super.getStyleableNode();
    }

    // JavaScript interface object
    public static class JavaApp {
        private final Label searchTerm = new Label("Search Term");

        @Override
        public String toString() {
            return "JavaScript App";
        }

        public String getSearchTerm() {
            return searchTerm.getText();
        }
    }


}