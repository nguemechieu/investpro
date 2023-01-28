module org.investpro.investpro {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.jetbrains.annotations;
    requires com.jfoenix;
    requires javafx.web;
    requires javafx.media;
    requires org.json;
    requires jdk.jsobject;
    requires java.logging;
    requires org.slf4j;
    requires com.fasterxml.jackson.databind;
    requires java.desktop;
    requires javafx.swing;
    requires java.sql;
    requires persistence.api;
    requires java.net.http;

    requires  java.datatransfer;
    opens org.investpro.investpro to javafx.fxml;
    exports org.investpro.investpro;
}