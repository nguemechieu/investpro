package org.investpro;


import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Example of how to use the CandleFX API to create a candle stick chart for the BTC/USD tradepair on Coinbase.
 */
public class CandleStickChartExample extends Application {
    private static final TradePair BTC_USD;
    private static final Logger logger = LoggerFactory.getLogger(CandleStickChartExample.class);

    static {
        try {
            BTC_USD = new TradePair("BTC", "USD");
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void start(@NotNull Stage primaryStage) throws UnsupportedEncodingException, NoSuchAlgorithmException, InvalidKeyException {
        Platform.setImplicitExit(false);
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> logger.error(STR."[\{thread}]: ", exception));
        CandleStickChartContainer candleStickChartContainer =
                new CandleStickChartContainer(
                        new Coinbase("wertt", "dfgth"), BTC_USD, true);
        AnchorPane.setTopAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setLeftAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setRightAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setBottomAnchor(candleStickChartContainer, 30.0);
        candleStickChartContainer.setMaxSize(Double.MAX_VALUE,
                Double.MAX_VALUE);
        Scene scene = new Scene(new AnchorPane(candleStickChartContainer), 1200, 800);
        scene.getStylesheets().add(Objects.requireNonNull(CandleStickChartExample.class.getResource("/app.css")).toExternalForm());
        primaryStage.setTitle("CandleFX - Candlestick Charts for JavaFX");
        primaryStage.setScene(scene);
        primaryStage.show();
    }


}
