package org.investpro.transfer;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class TransferHistoryService {

    private final ObservableList<TransferResult> history = FXCollections.observableArrayList();

    public ObservableList<TransferResult> history() {
        return history;
    }

    public void add(TransferResult result) {
        if (result != null) {
            history.add(0, result);
        }
    }
}
