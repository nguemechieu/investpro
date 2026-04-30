module investpro {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.slf4j;
    requires com.fasterxml.jackson.core;
    requires org.jetbrains.annotations;
    requires java.sql;
    requires Java.WebSocket;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires java.net.http;
    requires java.prefs;
    requires javafx.web;
    requires javafx.swing;
    requires org.json;
    requires java.desktop;
    requires org.xerial.sqlitejdbc;
    requires static lombok;
    requires com.nimbusds.jose.jwt;
    requires testcontainers;
    requires jakarta.mail;
    requires io.ebean.annotation;

    exports org.investpro;
    exports org.investpro.exchange;
    exports org.investpro.models.trading;
    exports org.investpro.models.currency;
    exports org.investpro.data;
    exports org.investpro.core.chat;
    exports org.investpro.ui;
    exports org.investpro.ui.charts;
    exports org.investpro.ui.tools;
    exports org.investpro.service;
    exports org.investpro.repository;
    exports org.investpro.indicators;
    exports org.investpro.utils;
    exports org.investpro.models;

}
