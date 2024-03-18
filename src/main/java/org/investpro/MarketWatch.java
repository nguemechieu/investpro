package org.investpro;

import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Objects;

public class MarketWatch extends Stage {
    public MarketWatch() {
        super(StageStyle.valueOf("MarketWatch"));
        setTitle("MarketWatch");
        getIcons().add(new javafx.scene.image.Image(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("icon.png"))));
        setResizable(false);

        AnchorPane anchorPane = new AnchorPane();
        anchorPane.setPrefSize(500, 400);

        setScene(new javafx.scene.Scene(anchorPane, 500, 400));
        show();
    }
}
