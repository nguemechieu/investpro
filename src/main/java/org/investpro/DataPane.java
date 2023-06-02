package org.investpro;

import com.jfoenix.controls.RecursiveTreeItem;
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.util.Callback;
import org.jetbrains.annotations.NotNull;

public class DataPane extends Parent {
    public DataPane(@NotNull Exchange exchange) {
        super();
        this.getStyleClass().add("app");
        this.setStyle("-fx-background-color: brown;");
        this.setStyle("-fx-border-color: black;");
        this.setStyle("-fx-border-width: 1px;");
        this.setStyle("-fx-border-style: solid;");
        this.setStyle("-fx-border-radius: 5px;");
        this.setStyle("-fx-padding: 10px;");
        this.setStyle("-fx-font-size: 20px;");
        this.setStyle("-fx-font-weight: bold;");
        this.setStyle("-fx-text-fill: white;");
        this.setStyle("-fx-alignment: center;");
        this.setStyle("-fx-padding: 10px;");
        this.setStyle("-fx-font-size: 20px;");

        TreeTableView<CandleData> treeTableView = new TreeTableView<>();
        treeTableView.setEditable(true);


        ObservableList<CandleData> data = FXCollections.observableArrayList();
        data.addAll(exchange.getCandleData());
        RecursiveTreeItem<CandleData> root =
                new RecursiveTreeItem<>(
                        data,
                        RecursiveTreeObject::getChildren);
        treeTableView.setRoot(root);
        treeTableView.setShowRoot(false);

        TreeTableColumn<CandleData, String> openColumn = new TreeTableColumn<>("Open");
        openColumn.setCellValueFactory(
                param -> new ReadOnlyStringWrapper(String.valueOf(param.getValue().getValue().getOpenPrice()))
        );
        TreeTableColumn<CandleData, String> highColumn = new TreeTableColumn<>("High");
        highColumn.setCellValueFactory(
                param -> new ReadOnlyStringWrapper(String.valueOf(param.getValue().getValue().getHighPrice()))
        );
        TreeTableColumn<CandleData, String> lowColumn = new TreeTableColumn<>("Low");
        lowColumn.setCellValueFactory(
                param -> new ReadOnlyStringWrapper(String.valueOf(param.getValue().getValue().getLowPrice()))
        );
        TreeTableColumn<CandleData, String> closeColumn = new TreeTableColumn<>("Close");
        closeColumn.setCellValueFactory(
                param -> new ReadOnlyStringWrapper(String.valueOf(param.getValue().getValue().getClosePrice()))
        );
        TreeTableColumn<CandleData, String> volumeColumn = new TreeTableColumn<>("Volume");
        volumeColumn.setCellValueFactory(
                param -> new ReadOnlyStringWrapper(String.valueOf(param.getValue().getValue().getVolume()))
        );
        TreeTableColumn<CandleData, String> timestampColumn = new TreeTableColumn<>("Timestamp");
        timestampColumn.setCellValueFactory(
                param -> new ReadOnlyStringWrapper(String.valueOf(param.getValue().getValue().getTimestamp()))
        );
        openColumn.setStyle("-fx-alignment: center; -fx-bacKground-color: green;");
        highColumn.setStyle("-fx-alignment: center;");
        lowColumn.setStyle("-fx-alignment: center;");
        closeColumn.setStyle("-fx-alignment: center;-fx-bacKground-color: red;");
        volumeColumn.setStyle("-fx-alignment: center;");
        timestampColumn.setStyle("-fx-alignment: center;");
        openColumn.setPrefWidth(150);
        highColumn.setPrefWidth(150);
        lowColumn.setPrefWidth(150);
        closeColumn.setPrefWidth(150);
        volumeColumn.setPrefWidth(150);
        timestampColumn.setPrefWidth(150);


        treeTableView.getColumns().addAll(
                openColumn,
                highColumn,
                lowColumn,
                closeColumn,
                volumeColumn,
                timestampColumn

        );
        root.setExpanded(true);
        root.setValue(
                exchange.getCandleData().get(0)
        );
        getChildren().addAll(new Label("Candle Data"), new Separator(Orientation.VERTICAL),
                treeTableView);

    }
}
