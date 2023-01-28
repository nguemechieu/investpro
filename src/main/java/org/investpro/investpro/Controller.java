package org.investpro.investpro;
import javafx.fxml.FXML;
import javafx.scene.control.*;


public class Controller {


    @FXML Label  username = new Label("Username:");
    @FXML TextField usernameField = new TextField("Enter username");

    @FXML Label passwordLabel = new Label("Password:");
    @FXML TextField passwordTextField = new TextField("Enter password");

    @FXML
    MenuBar menuBar = new MenuBar();
    @FXML
    Tooltip tooltip = new Tooltip() ;
    @FXML
    ToolBar toolBar = new ToolBar();
    @FXML SplitPane splitPane = new SplitPane();



}