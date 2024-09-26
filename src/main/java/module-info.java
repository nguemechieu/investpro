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
    requires io.github.cdimascio.dotenv.java;
    requires testcontainers;
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
    requires jakarta.xml.bind;
    requires org.slf4j;
    requires jdk.jfr;
    requires com.nimbusds.jose.jwt;
    requires org.bouncycastle.pkix;
    requires org.bouncycastle.provider;
    requires jdk.internal.le;

    requires  jakarta.inject;
    opens org.investpro to jakarta.cdi;
    exports org.investpro;

}