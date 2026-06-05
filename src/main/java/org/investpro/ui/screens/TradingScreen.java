package org.investpro.ui.screens;

import javafx.scene.Parent;
import org.investpro.persistence.repository.CurrencyRepository;
import org.investpro.persistence.repository.OrderRepository;
import org.investpro.persistence.repository.TradeRepository;
import org.investpro.ui.TradingDesk;
import org.investpro.ui.navigation.Screen;
import org.investpro.ui.theme.MarketConfiguration;

import java.util.Objects;

public class TradingScreen implements Screen {

    private final TradingDesk tradingDesk;
    private final Parent view;

    public TradingScreen(
            MarketConfiguration configuration,
            TradeRepository tradeRepository,
            OrderRepository orderRepository,
            CurrencyRepository currencyRepository) {
        this.tradingDesk = new TradingDesk(
                Objects.requireNonNull(configuration, "configuration must not be null"),
                Objects.requireNonNull(tradeRepository, "tradeRepository must not be null"),
                Objects.requireNonNull(orderRepository, "orderRepository must not be null"),
                Objects.requireNonNull(currencyRepository, "currencyRepository must not be null"));
        this.view = tradingDesk.getView();
    }

    @Override
    public Parent getView() {
        return view;
    }

    @Override
    public void onHide() {
        tradingDesk.shutdown();
    }
}
