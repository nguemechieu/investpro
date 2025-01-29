package org.investpro;

import javafx.scene.control.ListView;
import javafx.scene.layout.Region;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CoinInfoView extends Region {
    public CoinInfoView(@NotNull Exchange exchange) {
        getStyleClass().add("coin-info-view");

        List<CoinInfo> coinInfoList = CurrencyDataProvider.getCoinInfoList();

        ListView<CoinInfo> coinInfoListView = new ListView<>();
        coinInfoListView.getItems().addAll(coinInfoList);

        coinInfoListView.setPrefSize(1500, 700);

        getChildren().addAll(coinInfoListView);

    }
}
