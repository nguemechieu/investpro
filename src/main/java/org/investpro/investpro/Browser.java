package org.investpro.investpro;

import javafx.collections.ListChangeListener;
import javafx.concurrent.Worker;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class Browser {
//    static final String[] urls = new String[]{
//
//            "https://www.google.com/search?q",
//            "https://www.ebay.com",
//            "https://www.amazon.com",
//
//
//            "https://www.facebook.com"
//
//    };

    static String[] imageFiles = new String[]{
            "7123025_logo_google_g_icon.png",
            "8377-ebay_102466 (1).png",
            "amazon.png",
            "Facebook-PNG-Photos (1).png"};
    static ImageView selectedImage = new ImageView();
    static WebView smallView = new WebView();
    static
    WebView webWiew = new WebView();
    static final WebEngine webEngine = webWiew.getEngine();
    final Image[] images = new Image[imageFiles.length];
    final Button showPrevDoc = new Button("Toggle Previous Docs");
    String[] captions = new String[]{
            "Google",
            "Ebay",
            "Amazon",
            "Facebook"
    };
    final Hyperlink[] hpls = new Hyperlink[captions.length];
    HBox toolBar;
    ComboBox<String> comboBox = new ComboBox<>();
    boolean needDocumentationButton = false;

    public Browser() {
        //apply the styles
        //pane.getStyleClass().add("app.css");
        for (int i = 0; i < captions.length; i++) {
            // create hyperlinks
            Hyperlink hpl = hpls[i] = new Hyperlink(captions[i]);
            //  Image image = images[i] = new Image(Objects.requireNonNull(getClass().getResourceAsStream(imageFiles[i])));
            // hpl.setGraphic(new ImageView(image));
            //  String url = urls[i];


            boolean addButton = (hpl.getText().equals("Documentation"));
            // process event
            hpl.setOnAction(e -> {
                needDocumentationButton = addButton;
                webEngine.load(Objects.requireNonNull(getClass().getResourceAsStream(imageFiles[1])).toString());
            });
        }


        // create the toolbar
        toolBar = new HBox();
        toolBar.setAlignment(Pos.CENTER);
        toolBar.getStyleClass().add("browser-toolbar");
        toolBar.getChildren().add(comboBox);
        toolBar.getChildren().addAll(hpls);
        toolBar.getChildren().add(createSpacer());

        //set action for the button
        showPrevDoc.setOnAction(t -> webEngine.executeScript("toggleDisplay('PrevRel')"));

        smallView.setPrefSize(120, 80);
        //handle popup windows
        webEngine.setCreatePopupHandler(
                config -> {
                    smallView.setFontScale(0.8);
                    if (!toolBar.getChildren().contains(smallView)) {
                        toolBar.getChildren().add(smallView);
                    }
                    return smallView.getEngine();
                }
        );

        //process history
        WebHistory history = webEngine.getHistory();
        history.getEntries().addListener((ListChangeListener<WebHistory.Entry>) c -> {
            c.next();
            for (WebHistory.Entry e : c.getRemoved()) {
                comboBox.getItems().remove(e.getUrl());
            }
            for (WebHistory.Entry e : c.getAddedSubList()) {
                comboBox.getItems().add(e.getUrl());
            }
        });

        //set the behavior for the history combobox
        comboBox.setOnAction(ev -> {
            int offset = comboBox.getSelectionModel().getSelectedIndex() - history.getCurrentIndex();
            history.go(offset);
        });
        comboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                history.go(history.getCurrentIndex() + 1);
            }
        });
        // process page loading
        webEngine.getLoadWorker().stateProperty().addListener(
                (ov, oldState, newState) -> {
                    toolBar.getChildren().remove(showPrevDoc);
                    if (newState == Worker.State.SUCCEEDED) {
                        JSObject win = (JSObject) webEngine.executeScript("window");
                        win.setMember("app", new JavaApp());
                        win.setMember("showPrevDoc", showPrevDoc);
                        win.setMember("selectedImage", selectedImage);
                        if (needDocumentationButton) {
                            toolBar.getChildren().add(showPrevDoc);
                        }
                    }
                });

        // load the home pag
        webEngine.load("https://www.google.com");


    }


    @Contract(" -> new")
    public @NotNull Group start() throws Exception {

        DraggableTab tab1 = new DraggableTab("Google");
        TabPane tabPane = new TabPane();
        tabPane.getTabs().add(tab1);


        tab1.setClosable(true);
        WebView webWiew1 = new WebView();
        webWiew1.getEngine().load("https://www.google.com/search?q");
        tab1.setContent(webWiew1);
        tab1.setGraphic(new ImageView(selectedImage.getImage()));
        webWiew1.setPrefSize(1500, 780);


        DraggableTab tab2 = new DraggableTab("Amazon");
        tab2.setClosable(true);
        WebView webWiew2 = new WebView();
        webWiew2.getEngine().load("https://www.amazon.com");
        webWiew2.setPrefSize(1500, 780);


        tab2.setContent(webWiew2);
        webWiew1 = new WebView();
        webWiew1.getEngine().load("https://www.amazon.com/gp/search/search?q=amazon");
        tab2.setContent(webWiew1);
        tab2.setGraphic(new ImageView(selectedImage.getImage()));
        DraggableTab tab3 = new DraggableTab("EBAY");
        tab3.setText("Ebay");
        tab3.setGraphic(new ImageView(selectedImage.getImage()));
        WebView web3 = new WebView();
        web3.setPrefSize(1500, 780);


        web3.getEngine().load("https://www.ebay.com");
        VBox vb3 = new VBox(web3);
        tab3.setContent(vb3);
        tab3.setGraphic(new ImageView(selectedImage.getImage()));
        TabPane tab = new TabPane();
        tab.getTabs().addAll(tab1, tab2, tab3);
        Pane pane = new Pane();
        pane.getChildren().addAll(smallView, tab);
        pane.setPrefSize(1530, 780);

        return new Group(pane);
    }

    private @NotNull Node createSpacer() {
        Region region = new Region();
        region.getStyleClass().add("app");
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    // JavaScript interface object
    public static class JavaApp {
        @Override
        public String toString() {
            return "JavaScript App";
        }
    }


}