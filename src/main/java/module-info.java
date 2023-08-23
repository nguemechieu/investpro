module InvestPro {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.media;
    requires javafx.swing;
    requires java.sql;
    requires java.desktop;
    requires java.logging;
    requires java.prefs;
    requires jakarta.persistence;
    requires com.fasterxml.jackson.annotation;
    requires com.jfoenix;
    requires org.jetbrains.annotations;
    requires org.json;
    requires org.slf4j;

    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires java.net.http;
    requires javafx.web;
    requires com.fasterxml.jackson.dataformat.csv;
    requires org.testng;
    requires javax.websocket.api;
    requires com.google.gson;
    requires org.hibernate.orm.core;
    requires org.junit.jupiter.api;
    requires junit;
    requires Java.WebSocket;
    requires mysql.connector.j;


    opens org.investpro to javafx.fxml;
    exports org.investpro;
}