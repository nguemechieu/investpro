package org.investpro;

import javafx.application.Platform;
import javafx.scene.layout.AnchorPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Objects;

public class TradeView extends AnchorPane {
    private static final TradePair BTC_USD;
    private static final Logger logger = LoggerFactory.getLogger(InvestPro.class);

    static {
        try {
            BTC_USD = TradePair.of(Objects.requireNonNull(Currency.of("XLM")), Currency.ofFiat("USD"));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    TradeView() throws SQLException, ClassNotFoundException {
        Platform.setImplicitExit(false);
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> logger.error(STR."[\{thread}]: ", exception));

        CandleStickChartContainer candleStickChartContainer =
                new CandleStickChartContainer(
                        new Exchange("338904", "45N")
                        , true);
        AnchorPane.setTopAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setLeftAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setRightAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setBottomAnchor(candleStickChartContainer, 30.0);
        candleStickChartContainer.setMaxSize(Double.MAX_VALUE,
                Double.MAX_VALUE);

    }
}
