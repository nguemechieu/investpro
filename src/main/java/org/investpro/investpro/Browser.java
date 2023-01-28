package org.investpro.investpro;

import javafx.collections.ListChangeListener;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.geometry.*;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;
import org.jetbrains.annotations.NotNull;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Objects;
public   class Browser {
    private final HBox toolBar;
    static String[] imageFiles = new  String[]{
                   "7123025_logo_google_g_icon.png",
                  "8377-ebay_102466 (1).png",
                    "amazon.png",
                    "Facebook-PNG-Photos (1).png"};
    String[] captions = new String[]{
            "Google",
            "Ebay",
            "Amazon",
            "Facebook"
    };
    private static final String[] urls = new String[]{

            "https://www.google.com/search?q",
            "https://www.ebay.com",
            "https://www.amazon.com" ,


            "https://www.facebook.com"

    };
     ImageView selectedImage = new ImageView();
    final Hyperlink[] hpls = new Hyperlink[captions.length];
    final Image[] images = new Image[imageFiles.length];
    @FXML
    static
    WebView webWiew = new WebView();
    static final WebEngine webEngine = webWiew.getEngine();
    final Button showPrevDoc = new Button("Toggle Previous Docs");
    final WebView smallView = new WebView();
    final ComboBox<String> comboBox = new ComboBox<>();
    private boolean needDocumentationButton = false;
    public Browser() {
        //apply the styles
        pane.getStyleClass().add("app.css");
        for (int i = 0; i < captions.length; i++) {
            // create hyperlinks
            Hyperlink hpl = hpls[i] = new Hyperlink(captions[i]);
          //  Image image = images[i] = new Image(Objects.requireNonNull(getClass().getResourceAsStream(imageFiles[i])));
           // hpl.setGraphic(new ImageView(image));
           //  String url = urls[i];


             boolean addButton = (hpl.getText().equals("Documentation"));
            // process event
            hpl.setOnAction(e -> {needDocumentationButton = addButton;
                webEngine.load(Objects.requireNonNull(getClass().getResourceAsStream(imageFiles[1])).toString());});
        }

        comboBox.setPrefWidth(70);
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
                        toolBar.getChildren().add(smallView);}
                    return smallView.getEngine();
                }
        );

        //process history
        final WebHistory history = webEngine.getHistory();
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
                }
        );

        // load the home page

    }

    StackPane  pane = new StackPane();

    public void start(@NotNull Stage stage) throws Exception {
        webEngine.load("https://www.google.com/search?q");
        //add components
        TabPane tabPane= new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        DraggableTab tab0 = new DraggableTab("Home");

        tab0.setContent(webWiew);
        tab0.setText("Draggable");
        tab0.setContent(smallView);
        tab0.setClosable(true);
        webEngine.load("https://www.google.com/search?q");
        tab0.setGraphic(new ImageView(selectedImage.getImage()));
        tab0.setDetachable(true);
        tabPane.getTabs().add(tab0);
        Tab tab1 = new Tab("Google");
        tab1.setText("Google");
        tab1.setClosable(true);
        WebView webWiew1 =new WebView();
        webWiew1.getEngine().load("https://www.google.com/search?q");
        tab1.setContent(webWiew1);
        tab1.setGraphic(new ImageView(selectedImage.getImage()));
        Tab tab2 = new Tab();
        tab2.setText("Amazon");
        tab2.setClosable(true);
        WebView webWiew2 = new WebView();
        webWiew2.getEngine().load("https://www.amazon.com");
        tab2.setContent(webWiew2);
        webWiew1 = new WebView();
        webWiew1.getEngine().load("https://www.amazon.com/gp/search/search?q=amazon");
        tab2.setContent(webWiew1);
        tab2.setGraphic(new ImageView(selectedImage.getImage()));
        Tab tab3 = new Tab();
        tab3.setText("Ebay");
        tab3.setClosable(true);
        Spinner<News> spinner = new Spinner<>();

        spinner.setEditable(true);
        spinner.setMaxWidth(Double.MAX_VALUE);
        spinner.setMinWidth(Double.MAX_VALUE);
        spinner.setPrefWidth(Double.MAX_VALUE);
        spinner.setCacheShape(true);
        tab3.setContent(spinner);
        tab3.setGraphic(new ImageView(selectedImage.getImage()));
        TabPane tab= new TabPane();
        tab.getTabs().addAll(tab0, tab1, tab2, tab3);
        pane.getChildren().addAll(tab);
        pane.setMinSize(600,400);
        pane.setPrefSize(1530, 780);
        Group  group= new Group(pane);
        Scene scene = new Scene(group);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("app.css")).toExternalForm());

        stage.setResizable(true);
        stage.setIconified(true);
        stage.getIcons().add(
                new Image(Objects.requireNonNull(getClass().getResource("icon.png")).toExternalForm())
        );
        stage.setTitle("TradePro --->Browser "+ DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(LocalDateTime.now()));
        stage.setScene(scene);
        stage.show();
    }

    // JavaScript interface object
    public static class JavaApp {
        @Override
        public String toString() {
            return "JavaScript App";
        }
    }

    private @NotNull Node createSpacer() {
        Region region = new Region();
        region.getStyleClass().add("app");
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }


}