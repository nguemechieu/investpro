package org.investpro.investpro;

import javafx.scene.control.Alert;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;


public record UsersManager() {
    static User user;
    static DataSource db;
    static Alert alert = new Alert(Alert.AlertType.ERROR, "Please fill in all the fields");

    static {
        try {

            db = new DataSource();
            db.setUrl("jdbc:mysql://localhost:3306/db");
            db.setUser("root");
            db.setPassword("Bigboss307#");


        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        UsersManager.user = user;
    }

    public void RememberUser(boolean text) {


        if (text) {
            try {

                db.conn.setAutoCommit(false);
                db.conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                db.conn.close();
                db.conn.setAutoCommit(true);

            } catch (SQLException e) {
                e.printStackTrace();
            }

        }
    }

    public boolean VerifyUser(String username, String password) {
        if (!Objects.equals(username, "") && !Objects.equals(password, "")) {
            if (db.findOne("users", "username", username)) {
                return db.findOne("users", "password", password);
            }
        }
        return false;

    }

    void CreateAccount(
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
            user = new User(
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
            System.out.println(username + " " + password + " " + email + " " + firstname + " " + lastname + " " + middlename + " " + gender + " " + birthdate + " " + phone + " " + address + " " + city + " " + state + " " + country + " " + zipCode);
            if (db.findOne("users", "username", user.getUsername())) {
                System.out.println("Username already exists");

                alert = new Alert(Alert.AlertType.INFORMATION, "Username already exists");
                alert.showAndWait();


            } else if (db.findOne("users", "username", user.getUsername())) {
                System.out.println("Username already exists");
                Alert alert = new Alert(Alert.AlertType.ERROR, "Username already exists");
                alert.showAndWait();

            } else if (db.findOne("users", "password", user.getPassword())) {
                System.out.println("Password already exists");
                alert.setContentText("Password already exists");
                alert.showAndWait();

            } else if (db.create(user.getUsername(), user.getPassword(), user.getEmail(),
                    user.getFirstname(),
                    user.getLastname(),
                    user.getPhone(),
                    user.getMiddlename(),
                    user.getCountry(),
                    user.getCity(),
                    user.getState(),
                    user.getAddress(),
                    user.getZip()
            )) {

                alert.showAndWait();
                alert.setAlertType(
                        Alert.AlertType.INFORMATION);
                alert.setTitle("Registration");
                alert.setContentText("Registration Successful");
                alert.showAndWait();
                alert.showAndWait();

            } else {
                alert.showAndWait();
                alert.setAlertType(
                        Alert.AlertType.ERROR);
                alert.setTitle("Registration");
                alert.setContentText("Registration Failed");
                alert.showAndWait();


            }
        }


    }
}







