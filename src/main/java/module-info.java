module investpro {
    requires javafx.controls;
    requires javafx.fxml;

    requires com.fasterxml.jackson.core;
    requires org.jetbrains.annotations;
    requires Java.WebSocket;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires java.net.http;
    requires javafx.web;
    requires org.json;
    requires org.controlsfx.controls;
    requires com.nimbusds.jose.jwt;
    requires org.bouncycastle.provider;
    requires io.github.cdimascio.dotenv.java;
    requires testcontainers;
    requires com.google.auto.service;
    requires org.xerial.sqlitejdbc;
    requires javafx.swing;
    requires jdk.compiler;
    requires junit;
    requires cdi.api;
    requires weka.stable;

    requires bounce;
    requires com.google.gson;
    requires jakarta.persistence;
    requires org.hibernate.orm.core;
    requires jakarta.annotation;
    requires jakarta.xml.bind;
    requires ch.qos.logback.classic;
    requires org.slf4j;
    requires jdk.jfr;
    requires ch.qos.logback.core;
    requires jniloader;

    opens org.investpro to jakarta.cdi;
    //opens org.investpro to org.hibernate.orm.core;
    exports org.investpro;

}