package org.investpro;

import javafx.scene.Scene;
import javafx.stage.Stage;

public class StrategyTester extends Stage {

    public StrategyTester(Exchange exchange) {
        super();
        setTitle(
                "Strategy Tester"
        );
        setResizable(false);
        setScene(new Scene(
                new StrategyTesterView(exchange)
        ));
        show();
    }
}
