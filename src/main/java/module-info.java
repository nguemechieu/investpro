module org.investpro.investpro {

    uses org.investpro.CodecFactory;
    uses org.investpro.ServiceProvider;
    requires javafx.controls;
    requires javafx.fxml;




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
    requires javafx.base;

    requires javafx.graphics;

    requires javafx.mediaEmpty;
    requires javafx.web;
    requires org.slf4j;

    opens org.investpro to javafx.fxml;
    exports org.investpro;

}