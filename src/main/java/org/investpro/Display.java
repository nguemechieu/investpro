package org.investpro;

import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;

import java.sql.SQLException;

public class Display extends VBox {
    TradePair BTC_USD;

    public Display() throws SQLException, ClassNotFoundException {
        super();


        Exchange exchange = new Exchange("9jnerlff23u8ed01np9g6ysbhsh0dvcs", "ax5kh6jaqkcd2tiexxs8v6xjo8yv8a6b");

        CandleStickChartContainer candleStickChartContainer = new CandleStickChartContainer(exchange);


        candleStickChartContainer.setTranslateY(100);
        setLayoutY(50);


        setMaxSize(
                getMaxWidth(), getMaxHeight()
        );
        candleStickChartContainer.setPrefSize(1300, 500);
        candleStickChartContainer.setTranslateY(30);
        getChildren().setAll(candleStickChartContainer);


    }
}
