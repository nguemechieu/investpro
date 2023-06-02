package org.investpro;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;

import java.util.Date;

public class NewsView extends TreeTableView<News> {

    public NewsView() {
        super();

        this.setPrefHeight(780);
        this.setPrefWidth(1530);


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
        TreeItem<News> root = new TreeItem<>();
        root.setExpanded(true);

root.setValue(
        new News(
                "photo",
                "2020-01-,","",
                new Date(),
                "Crypto Investor is a cryptocurrency investment platform based on blockchain technology.",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        )
);

getColumns().addAll(
                titleColumn,
                dateColumn,
                countryColumn,
                impactColumn,
                forecastColumn,
                previousColumn
        );





    }


}
