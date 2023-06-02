package org.investpro;

public class OrderCell extends javafx.scene.control.ListCell<Order> {
    @Override
    protected void updateItem(Order item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
            setText(null);
        } else {
            setText(item.toString());
        }
    }
}
