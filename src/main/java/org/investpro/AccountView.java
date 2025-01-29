package org.investpro;

import javafx.scene.control.ListView;
import javafx.scene.layout.AnchorPane;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * AccountAnchor is a class that displays detailed account information on a graphical canvas.
 * It pulls account data from the provided Exchange object and presents it in a well-formatted manner.
 */
public class AccountView extends AnchorPane {

    public AccountView(@NotNull Exchange exchange) throws IOException, NoSuchAlgorithmException, InvalidKeyException, InterruptedException {

        // Fetch the account details from the exchange
        List<Account> account = exchange.getAccounts();


        ListView<Account> listView = new ListView<>();

        listView.getItems().addAll(account);
        AnchorPane.setTopAnchor(listView, 0.0);
        AnchorPane.setLeftAnchor(listView, 0.0);
        AnchorPane.setRightAnchor(listView, 0.0);
        AnchorPane.setBottomAnchor(listView, 0.0);
        getChildren().add(listView);
        setPrefSize(
                1500, 700
        );
    }
}
