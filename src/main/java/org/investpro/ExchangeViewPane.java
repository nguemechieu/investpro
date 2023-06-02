package org.investpro;

import javafx.scene.control.TabPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ExchangeViewPane extends TabPane {

    final Logger logger = LoggerFactory.getLogger(ExchangeViewPane.class);
    List<ENUM_EXCHANGE_LIST> exChangeList = new ArrayList<>(

    );

    public ExchangeViewPane() {
        super();
        exChangeList.add(ENUM_EXCHANGE_LIST.BINANCE_US);
        exChangeList.add(ENUM_EXCHANGE_LIST.COINBASE_PRO);
        exChangeList.add(ENUM_EXCHANGE_LIST.OANDA);
//        exChangeList.add(ENUM_EXCHANGE_LIST.KRAKEN);
//        exChangeList.add(ENUM_EXCHANGE_LIST.KUCOIN);
//        exChangeList.add(ENUM_EXCHANGE_LIST.BITFINEX);
//        exChangeList.add(ENUM_EXCHANGE_LIST.BITMEX);
//        exChangeList.add(ENUM_EXCHANGE_LIST.BITSTAMP);

        for (ENUM_EXCHANGE_LIST enumExchangeList : exChangeList) {
            ENUM_EXCHANGE_LIST i =
                    ENUM_EXCHANGE_LIST.valueOf(enumExchangeList.name());
            DraggableTab tab = new DraggableTab(enumExchangeList.name(), i.getIcon());
            getTabs().add(tab);
            AnchorPane pane = new AnchorPane();
            VBox vbox = new VBox();

            pane.getChildren().add(vbox);
            tab.setContent(pane);

        }

        setTabDragPolicy(TabDragPolicy.REORDER);
        setTabClosingPolicy(TabClosingPolicy.SELECTED_TAB);


        logger.info("ExchangeViewPane created");
    }


}
