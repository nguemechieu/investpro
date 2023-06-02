package org.investpro;


import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;


public class CascadeWindow extends Region {


    public CascadeWindow() {
        StackPane stackPane = new StackPane();
        VBox vb = new VBox();
        stackPane.getChildren().addAll(vb);

        Stage stage = new Stage();
        stage.setScene(new Scene(stackPane, 800, 600));
        stage.show();

    }

}
