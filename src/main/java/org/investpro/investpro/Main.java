package org.investpro.investpro;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.util.Date;

public class Main extends Application {
    @Override
    public void start(@NotNull Stage stage) {
        createLogin(stage);
    }

    private void createRegister(@NotNull ActionEvent e) {

        Node node = (Node) e.getSource();
        Stage stage = (Stage) node.getScene().getWindow();
        stage.close();

        GridPane grid = new GridPane();
        Node lblusername = new Label("Username :");
        grid.add(lblusername, 0, 0);
        TextField textUsername = new TextField();
        textUsername.setPromptText("Enter username");
        grid.add(textUsername, 0, 1);

        Node lblPassword = new PasswordField();

        grid.add(lblPassword, 0, 2);
        PasswordField passwordField = new PasswordField();
        grid.add(passwordField, 1, 2);
        Button btnSignIn = new Button("Go Back");
        grid.add(btnSignIn, 0, 7);
        btnSignIn.setOnAction(eb -> createLogin(new Stage()));
        Button lblRegister = new Button("Submit");

        grid.add(lblRegister, 2, 7);


        AnchorPane anchorPane = new AnchorPane(grid);
        Scene scene = new Scene(anchorPane, 1530, 780);
        stage.setScene(scene);
        stage.setTitle("InvestPro -->Registration");
        stage.setResizable(true);
        stage.setIconified(true);
        stage.show();

        lblRegister.setOnAction(this::createMainMenu);

    }

    private void createLogin(@NotNull Stage stage) {
        AnchorPane anchorPane = new AnchorPane();

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10, 10, 10, 10));
        grid.setTranslateX(500);
        grid.setTranslateY(200);
        stage.close();
        Label lblUsername = new Label("Username :");
        TextField textField = new TextField("");
        textField.setPromptText("Enter username");
        Label lblPassword = new Label("Password :");
        PasswordField passwordField = new PasswordField();
        grid.add(lblUsername, 0, 0);
        grid.add(textField, 1, 0);
        grid.add(lblPassword, 0, 1);
        grid.add(passwordField, 1, 1);
        Button btnReg = new Button("Register");
        grid.add(btnReg, 0, 7);
        Button btnLgn = new Button("Login");
        grid.add(btnLgn, 2, 7);
        Hyperlink btnForget = new Hyperlink("Forgot Password");
        grid.add(btnForget, 1, 12);
        anchorPane.getChildren().add(grid);
        Scene scene = new Scene(anchorPane, 1530, 780);
        stage.setTitle("InvestPro   " + new Date(System.currentTimeMillis()));
        stage.setScene(scene);
        stage.setResizable(true);
        stage.setIconified(true);


        btnLgn.setOnAction(er -> createMainMenu(er));

        btnForget.setOnAction(et -> createForgotPassword(et));
        btnReg.setOnAction(eb -> createRegister(eb));

        stage.show();
    }

    private void createForgotPassword(@NotNull ActionEvent e) {
        Node node = (Node) e.getSource();
        Stage stage = (Stage) node.getScene().getWindow();
        stage.close();

        stage.setTitle("Forgot Password " + new Date(System.currentTimeMillis()));
        AnchorPane anchorPane = new AnchorPane();
        Scene scene = new Scene(anchorPane, 1530, 780);
        stage.setScene(scene);
        stage.setIconified(true);
        stage.setResizable(true);
        stage.show();
    }

    private void createMainMenu( @NotNull ActionEvent e) {
        Node node = (Node) e.getSource();
        Stage stage = (Stage) node.getScene().getWindow();

        AnchorPane anchorPane = new AnchorPane();
        Scene scene = new Scene(anchorPane, 1530, 780);
        scene.getStylesheets().addAll("app.css");
        stage.setScene(scene);
        stage.setTitle("Trading Panel");
        stage.setResizable(true);
        stage.setIconified(true);
        stage.show();

    }

    public static void main(String[] args) {
        launch(Main.class,args);
    }
}