package org.investpro;

import com.jfoenix.controls.RecursiveTreeItem;
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.Objects;
import java.util.Properties;

import static org.investpro.NewsManager.load;

public class TradingWindow extends AnchorPane {
    private static final Logger logger = LoggerFactory.getLogger(TradingWindow.class);
    public TradingWindow() throws Throwable {
        super();

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);
           for (ENUM_EXCHANGE_LIST i : ENUM_EXCHANGE_LIST.values()) {
               DraggableTab tab = new DraggableTab(i.name(), "/InvestPro.png");
               if (i.getIcon() != null) {
                   tab.setGraphic(new ImageView((String) i.getIcon()));
               }
               tabPane.getTabs().add(tab);
               @NotNull Exchange exchange;

               exchange = switch (i) {
//                case
//                        BINANCE:
//                    new Binance(
//                            api_key,
//                            api_secret,
//                            account_id);
                   case BINANCE_US -> new BinanceUs(
                           "odkr6pfbgl10ZM7i2D4kZ8FgOZLDjzs3iAY2IV2E67Cm316dkQs397bScVzhH4b1","3ilQvixaFv3Y1sdZ48jO0JqShoQUU6SdQkbviOAgIB2zHR9xu8J7hQSPZGqzxoTd");
//                case BITSTAMP: {
//
//                     new Bitstamp(
//                            api_key,
//                            api_secret,
//                            account_id);
//
//                }
//               case BITFINEX:
//                    exchange = new Bitfinex(
//                            api_key,
//
//                            api_secret,
//                            account_id
//                    );
//                    break;
//                case BITTREX:
//                    exchange =
//                            new Bittrex(
//
//                                    api_key,
//
//                                    api_secret,
//                                    account_id
//                            );
//                    break;

                   case OANDA -> new Oanda("001-001-2783446-002",

                           "690b43930d606088874f601a03c254d1-ff286fb3b7ba0459d8db9398dc6297f6");
//                case POLONIEX:
//                    exchange = new Poloniex(
//
//                            api_key,
//                            api_secret,
//                            account_id
//                    );
//                    break;
//                case KUCOIN:
//                    exchange = new Kucoin(
//
//                            api_key,
//                            api_secret,
//                            account_id
//                    );
//                    break;

                   default -> new Coinbase(
                           "gUl2gfk/zu9o6rqicLtBokMupgGG3j8AqC1kQvZfOj8qDUQdPT0dhDiK0NIOkFPLsGNQ9MjfYtIBHKSieQaJDw==",
                           "zdkva105scm"


                   );
               };

               tab.setContent(
                       new VBox(new Label(i.name(),
                               new Separator(Orientation.VERTICAL)),
                               new TradeView(exchange, "2125623831:AAGtuhGO9JxHh72nYfD6WN6mog7UkDIIL0o")));
           }

        tabPane.getTabs().addAll(getNewsTab(),browserTab());
       setPrefSize(1530, 780);
        getStyleClass().add("trading-window");
        getStylesheets().add(Objects.requireNonNull(getClass().getResource("/app.css")).toExternalForm());
        tabPane.setTranslateY(25);

        logger.debug("TradingWindow initialized");
        getChildren().addAll(getMenuBar(), tabPane);

    Properties properties = new Properties();
    properties.load(Objects.requireNonNull(getClass().getResourceAsStream("/app.properties")));
    logger.debug("Properties loaded");
    logger.info("properties "+properties.values());
    }

    private @NotNull Tab browserTab() {

        return new DraggableTab("Browser", "");
    }

    private @NotNull Tab getNewsTab() throws ParseException {
        Tab newsTab = new Tab("News");
        TreeTableView<News>tree = new TreeTableView<>();

        ObservableList<News> ob= FXCollections.observableArrayList();
        ob.addAll(load());
        RecursiveTreeItem<News> root = new RecursiveTreeItem<>(ob, RecursiveTreeObject::getChildren);
           root.setExpanded(true);
           root.setValue(
                   ob.get(0)
           );



        tree.setRoot(root);
        setPrefHeight(780);
        setPrefWidth(1530);
        setPrefSize(1530, 780);
        TreeTableColumn<News,String> titleColumn = new TreeTableColumn<>("Title");

        titleColumn.setCellValueFactory(param ->new ReadOnlyStringWrapper( param.getValue().getValue().getTitle()));
        TreeTableColumn<News,String> dateColumn = new TreeTableColumn<>("Date");
        dateColumn.setCellValueFactory(param ->new ReadOnlyStringWrapper( param.getValue().getValue().getDate().toString()));
        TreeTableColumn<News,String> impactColumn = new TreeTableColumn<>("Impact");
        impactColumn.setCellValueFactory(param ->new ReadOnlyStringWrapper( param.getValue().getValue().getImpact()));

        TreeTableColumn<News,String>  forecastColumn = new TreeTableColumn<>("Forecast");
        forecastColumn.setCellValueFactory(param ->new ReadOnlyStringWrapper( param.getValue().getValue().getForecast()));
        TreeTableColumn<News,String> previousColumn = new TreeTableColumn<>("Previous");
        previousColumn.setCellValueFactory(param ->new ReadOnlyStringWrapper( param.getValue().getValue().getPrevious()));
        TreeTableColumn<News,String> countryColumn = new TreeTableColumn<>("Country");
        countryColumn.setCellValueFactory(param ->new ReadOnlyStringWrapper( param.getValue().getValue().getCountry()));

      tree.getColumns().add(titleColumn);
      tree.getColumns().add(dateColumn);
      tree.getColumns().add(countryColumn);
      tree.getColumns().add(impactColumn);
      tree.getColumns().add(forecastColumn);
      tree.getColumns().add(previousColumn);
      newsTab.setContent(tree);
        return newsTab;
    }

    public MenuBar getMenuBar() {
        Menu fileMenu = new Menu("File");
        fileMenu.getItems().add(
                new MenuItem("New")
        );


        fileMenu.getItems().add(new SeparatorMenuItem());
        fileMenu.getItems().add(
                new MenuItem("Open")
        );
        fileMenu.getItems().add(new SeparatorMenuItem());
        fileMenu.getItems().add(
                new MenuItem("Save")
        );
        fileMenu.getItems().add(new SeparatorMenuItem());
        fileMenu.getItems().add(
                new MenuItem("Save As")
        );
        fileMenu.getItems().add(new SeparatorMenuItem());
        fileMenu.getItems().add(
                new MenuItem("Print")
        );
        fileMenu.getItems().add(
                new MenuItem("Exit")
        );


        Menu editMenu = new Menu("Edit");
        editMenu.getItems().add(new SeparatorMenuItem());
        editMenu.getItems().add(
                new MenuItem("Cut")
        );
        editMenu.getItems().add(new SeparatorMenuItem());
        editMenu.getItems().add(
                new MenuItem("Copy")
        );
        editMenu.getItems().add(new SeparatorMenuItem());
        editMenu.getItems().add(
                new MenuItem("Paste")
        );
        editMenu.getItems().add(
                new MenuItem("Delete")
        );
        Menu viewMenu = new Menu("View");
        viewMenu.getItems().add(new MenuItem("Zoom In"));
        viewMenu.getItems().add(new SeparatorMenuItem());
        viewMenu.getItems().add(new MenuItem("Zoom Out"));
        viewMenu.getItems().add(new SeparatorMenuItem());
        viewMenu.getItems().add(new MenuItem("Reset Zoom"));
        viewMenu.getItems().add(new SeparatorMenuItem());
        viewMenu.getItems().add(new MenuItem("Reset"));
        Menu windowMenu = new Menu("Window");
        windowMenu.getItems().add(new MenuItem("Minimize"));
        windowMenu.getItems().add(new MenuItem("Maximize"));
        windowMenu.getItems().add(new MenuItem("Close"));

        Menu chartMenu = new Menu("Charts");
        chartMenu.getItems().add(new MenuItem("Bar Chart"));
        chartMenu.getItems().add(new SeparatorMenuItem());
        chartMenu.getItems().add(new MenuItem("Line Chart"));
        chartMenu.getItems().add(new SeparatorMenuItem());
        chartMenu.getItems().add(new MenuItem("Pie Chart"));
        chartMenu.getItems().add(new SeparatorMenuItem());
        chartMenu.getItems().add(new MenuItem("Scatter Chart"));
        chartMenu.getItems().add(new SeparatorMenuItem());
        chartMenu.getItems().add(new MenuItem("Radar Chart"));
        chartMenu.getItems().add(new SeparatorMenuItem());
        chartMenu.getItems().add(new MenuItem("Bubble Chart"));
        chartMenu.getItems().add(new SeparatorMenuItem());
        chartMenu.getItems().add(new MenuItem("Candle Stick Chart"));
        Menu insertMenu = new Menu("Insert");
        insertMenu.getItems().add(new Menu("Indicators"));
        insertMenu.getItems().add(new SeparatorMenuItem());
        Menu lines = new Menu("Lines");
        lines.getItems().add(new MenuItem("Trend Line By Angle"));
        lines.getItems().add(new SeparatorMenuItem());
        lines.getItems().add(new MenuItem("Vertical Line"));
        lines.getItems().add(new SeparatorMenuItem());
        lines.getItems().add(new MenuItem("Horizontal Line"));
        lines.getItems().add(new SeparatorMenuItem());
        lines.getItems().add(new MenuItem("Trend Line"));
        lines.getItems().add(new SeparatorMenuItem());
        insertMenu.getItems().add(lines);
        insertMenu.getItems().add(new Menu("Areas"));
        insertMenu.getItems().add(new SeparatorMenuItem());
        insertMenu.getItems().add(new Menu("Channels"));
        insertMenu.getItems().add(new SeparatorMenuItem());
        insertMenu.getItems().add(new Menu("Markers"));
        insertMenu.getItems().add(new SeparatorMenuItem());
        insertMenu.getItems().add(new Menu("Gann"));
        insertMenu.getItems().add(new SeparatorMenuItem());
        insertMenu.getItems().add(new Menu("Fibonacci"));
        insertMenu.getItems().add(new SeparatorMenuItem());
        insertMenu.getItems().add(new Menu("Arrows"));
        insertMenu.getItems().add(new SeparatorMenuItem());
        insertMenu.getItems().add(new Menu("Shapes"));
        insertMenu.getItems().add(new SeparatorMenuItem());
        insertMenu.getItems().add(new Menu("Objects"));
        Menu helpMenu = new Menu("Help");
        helpMenu.getItems().add(new MenuItem("About"));
        helpMenu.getItems().add(new SeparatorMenuItem());
        helpMenu.getItems().add(new MenuItem("Help"));


        MenuBar menuBar = new MenuBar();
        menuBar.getMenus().addAll(
                fileMenu,
                editMenu,
                viewMenu,
                windowMenu,
                chartMenu,
                insertMenu,
                helpMenu);

        Menu toolsMenu = new Menu("Tools");
        toolsMenu.getItems().add(new MenuItem("New Order"));
        toolsMenu.getItems().add(new SeparatorMenuItem());
        toolsMenu.getItems().add(new MenuItem("History Center"));

        toolsMenu.getItems().add(new MenuItem("Balance Sheet"));
        return menuBar;
    }

}
