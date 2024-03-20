package org.investpro;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

public class CreateAccount extends Stage {
    public CreateAccount() {
        super();
        AnchorPane anc = new AnchorPane();
        anc.getChildren().add(
                new Label("Create an account")

        );

        WebView webView = new WebView();
        webView.getEngine().load("https://www.google.com");
        anc.getChildren().add(webView);

        Scene scene = new Scene(anc, 500, 500);

        scene.getStylesheets().add("/app.css");
    }
}
