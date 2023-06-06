module org.investpro.investpro {

    uses org.investpro.CodecFactory;
    uses org.investpro.ServiceProvider;
    uses org.investpro.CurrencyDataProvider;

    requires javafx.fxml;
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

    requires java.prefs;
    requires jakarta.persistence;
    requires javax.websocket.api;
    requires com.google.gson;
    requires com.fasterxml.jackson.dataformat.csv;
    requires org.junit.jupiter.api;

    requires org.hibernate.orm.core;
    requires org.testng;

    requires javafx.media;
    requires javafx.web;
    requires org.slf4j;
    requires javafx.controls;

    opens org.investpro to javafx.fxml;
    exports org.investpro;

}