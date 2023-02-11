module org.investpro.investpro {
    uses org.investpro.investpro.CurrencyDataProvider;

    uses org.investpro.investpro.CodecFactory;
    requires javafx.controls;
    requires javafx.fxml;
    requires org.jetbrains.annotations;
    requires com.jfoenix;
    requires javafx.web;
    requires javafx.media;
    requires org.json;
    requires jdk.jsobject;
    requires java.logging;

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
    opens org.investpro.investpro to javafx.fxml;
    exports org.investpro.investpro;
    exports org.investpro.investpro.Coinbase;
    opens org.investpro.investpro.Coinbase to javafx.fxml;
    exports org.investpro.investpro.BinanceUs;
    opens org.investpro.investpro.BinanceUs to javafx.fxml;
    exports org.investpro.investpro.oanda;
    opens org.investpro.investpro.oanda to javafx.fxml;
}