package org.investpro;

import javafx.scene.Scene;
import javafx.stage.Stage;

public class DataWindow extends Stage {

    public DataWindow(Exchange exchange) {
        super();
        this.setTitle("Data     Window");
        this.setResizable(true);
        this.centerOnScreen();

        Scene scene = new Scene(new DataPane(exchange));
        this.setScene(scene);

        this.show();


    }

}
