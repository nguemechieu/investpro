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
        AnchorPane anchorPane=new AnchorPane();

        GridPane  grid=new GridPane();
        grid.setPadding(new  Insets(9,10,10,10));
        grid.setTranslateX(400);
        grid.setTranslateY(200);
        grid.setGridLinesVisible(true);
        Label lblUsername=new Label("Username :");
        TextField textField=new TextField("");
        textField.setPromptText("Enter username");
        Label lblPassword=new Label("Password :");
        PasswordField passwordField=new PasswordField();
        grid.add(lblUsername,0,0);
        grid.add(textField,1,0);
        grid.add(lblPassword,0,1);
        grid.add(passwordField,1,1);
        Button btnReg=new Button("Register");
        grid.add(btnReg,0,7);
        Button btnLgn=new Button("Login");
        grid.add(btnLgn,2,7);
        Hyperlink btnForget=new Hyperlink("Forgot Password");
        grid.add(btnForget, 1,12);
        anchorPane.getChildren().add(grid);
        Scene scene = new Scene(anchorPane,1530,780);
        stage.setTitle("InvestPro   "+ new Date(System.currentTimeMillis()));
        stage.setScene(scene);
        stage.setResizable(true);
        stage.show();

        btnLgn.setOnAction(this::createMainMenu);

        btnForget.setOnAction(this::createForgotPassword);
        btnReg.setOnAction(this::createRegister);
    }

    private void createRegister(@NotNull ActionEvent e) {

        Node node= (Node)e.getSource();
        Stage stage =(Stage) node.getScene().getWindow();
        stage.close();


        stage =new Stage();
        AnchorPane anchorPane = new AnchorPane();
        Scene scene=new Scene(anchorPane,1530,780);
        stage.setScene(scene);


        stage.setTitle("InvestPro -->Registration" );
        stage.setResizable(true);
        stage.setIconified(true);
        stage.show();


    }

    private void createForgotPassword( @NotNull ActionEvent e)
    {
        Node node= (Node) e.getSource();
       Stage stage =(Stage) node.getScene().getWindow();
       stage.close();
        stage =new Stage();
        stage.setTitle("Forgot Password "+ new Date(System.currentTimeMillis()));
        AnchorPane anchorPane = new AnchorPane();
        Scene scene=new Scene(anchorPane,1530,780);
        stage.setScene(scene);
stage.setIconified(true);

        stage.setResizable(true);
        stage.show();
    }

    private void createMainMenu( @NotNull ActionEvent e) {
      Node node =  (Node) e.getSource();
      Stage  stage = (Stage) node.getScene().getWindow();

        stage.close();
        stage =new Stage();
        AnchorPane anchorPane = new AnchorPane();
        Scene scene=new Scene(anchorPane,1530,780);
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