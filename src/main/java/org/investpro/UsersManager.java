package org.investpro;

import javafx.scene.control.Alert;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;

import static org.investpro.Currency.db1;


public record UsersManager() {
    static users user;

    static Alert alert = new Alert(Alert.AlertType.ERROR, "Please fill in all the fields");


    public users getUser() {
        return user;
    }

    public void setUser(users user) {
        UsersManager.user = user;
    }

    public void RememberUser(boolean text) {
        if (text) {
            try {

                Db1 db = db1;
                db.conn.setAutoCommit(false);
                db.conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                db.conn.close();
                db.conn.setAutoCommit(true);

            } catch (SQLException e) {
                e.printStackTrace();
            }

        }
    }



    public  void CreateAccount(
            @NotNull String username,
            @NotNull String password,
            @NotNull String email,
            @NotNull String firstname,
            @NotNull String lastname,
            @NotNull String middlename,
            @NotNull String gender,
            @NotNull String birthdate,
            @NotNull String phone,
            @NotNull String address,
            @NotNull String city,
            @NotNull String state,
            @NotNull String country,
            @NotNull String zipCode) throws SQLException, ClassNotFoundException, InstantiationException, IllegalAccessException {

        if (
                username.equals("") || password.equals("") || email.equals("") || city.equals("") || state.equals("") || country.equals(" ") || firstname.equals("") || lastname.equals("") || middlename.equals("") || gender.equals("") || birthdate.equals("") || country.equals("") || phone.equals("") || address.equals("")
        ) {
            alert.showAndWait();

        } else {
            user = new users(
                    username,
                    password,
                    email,
                    firstname,
                    lastname,
                    middlename,
                    gender,
                    birthdate,
                    phone,
                    address,
                    city,
                    state,
                    country,
                    zipCode);

        }
    }
}







