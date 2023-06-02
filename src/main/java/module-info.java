module org.investpro.investpro {
    uses org.investpro.CurrencyDataProvider;

    uses org.investpro.CodecFactory;
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.mediaEmpty;
    requires javafx.graphics;

    requires org.jetbrains.annotations;
    requires com.jfoenix;

    requires org.json;
    requires jdk.jsobject;
    requires java.logging;
    requires java.xml;


    requires com.fasterxml.jackson.databind;
    requires java.desktop;
    requires javafx.swing;
    requires java.sql;
    requires persistence.api;
    requires java.net.http;

    requires java.datatransfer;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires Java.WebSocket;
    requires org.controlsfx.controls;
    requires mysql.connector.j;
    requires jdk.hotspot.agent;
    requires testcontainers;
    requires java.rmi;


    opens org.investpro to javafx.fxml;
    exports org.investpro;

}