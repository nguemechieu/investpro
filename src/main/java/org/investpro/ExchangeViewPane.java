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

        for (ENUM_EXCHANGE_LIST enumExchangeList : exChangeList) {

            DraggableTab tab = new DraggableTab(enumExchangeList.name());
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
