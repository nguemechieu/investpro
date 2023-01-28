module org.investpro.investpro {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.jetbrains.annotations;


    opens org.investpro.investpro to javafx.fxml;
    exports org.investpro.investpro;
}